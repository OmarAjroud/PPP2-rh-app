package com.hrapp.rh.assistant;

import com.hrapp.rh.securite.UtilisateurConnecte;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * Serveur MCP (Model Context Protocol) de l'assistant RH, sur POST /mcp.
 *
 * Mode « stateless » : chaque requête HTTP est autonome et porte son JWT (en-tête Authorization),
 * vérifié par le filtre de sécurité habituel avant d'arriver ici. Les outils sont ceux du chat
 * (OutilsRhService), exécutés avec les droits de l'utilisateur du jeton, et journalisés.
 */
@Configuration
public class McpServerConfig {

    static final String ENDPOINT = "/mcp";
    static final String CLE_UTILISATEUR = "utilisateur";

    @Bean
    public HttpServletStatelessServerTransport mcpTransport() {
        return HttpServletStatelessServerTransport.builder()
                .jsonMapper(new JacksonMcpJsonMapper(JsonMapper.builder().build()))
                .messageEndpoint(ENDPOINT)
                .contextExtractor(McpServerConfig::extractUser)
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStatelessServerTransport> mcpServlet(
            HttpServletStatelessServerTransport transport) {
        ServletRegistrationBean<HttpServletStatelessServerTransport> enregistrement =
                new ServletRegistrationBean<>(transport, ENDPOINT);
        enregistrement.setName("mcp");
        enregistrement.setAsyncSupported(true);
        return enregistrement;
    }

    @Bean(destroyMethod = "close")
    public McpStatelessSyncServer mcpServer(HttpServletStatelessServerTransport transport, OutilsRhService outils) {
        return McpServer.sync(transport)
                .serverInfo("rh-assistant", "1.0.0")
                .instructions("Outils RH en lecture seule. Chaque outil s'exécute avec les droits de l'utilisateur "
                        + "du jeton : un refus d'accès est normal et ne doit pas être contourné.")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .tools(specifications(outils))
                .build();
    }

    // ---------- méthodes privées ----------

    static List<McpStatelessServerFeatures.SyncToolSpecification> specifications(OutilsRhService outils) {
        return outils.definitions().stream().map(outil -> McpStatelessServerFeatures.SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder()
                        .name(outil.nom())
                        .description(outil.description())
                        .inputSchema(Map.of("type", "object",
                                "properties", outil.proprietes(),
                                "required", outil.obligatoires()))
                        // lecture seule, sans effet de bord : les clients MCP peuvent l'exécuter sans confirmation
                        .annotations(new McpSchema.ToolAnnotations(outil.nom(), true, false, true, false, null))
                        .build())
                .callHandler((contexte, requete) -> call(outils, contexte, requete))
                .build()).toList();
    }

    static McpSchema.CallToolResult call(OutilsRhService outils, McpTransportContext contexte,
                                          McpSchema.CallToolRequest requete) {
        if (!(contexte.get(CLE_UTILISATEUR) instanceof UtilisateurConnecte utilisateur)) {
            // ne devrait pas arriver : /mcp exige un JWT valide
            return McpSchema.CallToolResult.builder().addTextContent("Authentification requise").isError(true).build();
        }
        ResultatOutil resultat = outils.execute(utilisateur, requete.name(), requete.arguments(),
                OutilsRhService.Source.MCP);
        return McpSchema.CallToolResult.builder()
                .addTextContent(resultat.contenu())
                .isError(resultat.erreur())
                .build();
    }

    /** Récupère l'utilisateur authentifié par le filtre JWT (Spring Security l'expose comme principal de la requête). */
    private static McpTransportContext extractUser(HttpServletRequest requete) {
        if (requete.getUserPrincipal() instanceof AbstractAuthenticationToken jeton
                && jeton.getPrincipal() instanceof UtilisateurConnecte utilisateur) {
            return McpTransportContext.create(Map.of(CLE_UTILISATEUR, utilisateur));
        }
        return McpTransportContext.EMPTY;
    }
}

package com.hrapp.rh.assistant;

import com.hrapp.rh.securite.Roles;
import com.hrapp.rh.securite.UtilisateurConnecte;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class McpServerConfigTest {

    @Mock private OutilsRhService outils;

    private final UtilisateurConnecte karim = new UtilisateurConnecte(3L, 3L, 1L, "manager", "hash", Roles.MANAGER, true);

    @Test
    @DisplayName("Appel MCP : exécuté avec l'utilisateur du jeton, source MCP")
    void call_shouldRunToolAsTokenUser() {
        Map<String, Object> args = Map.of("employeId", 4);
        given(outils.execute(karim, "consulter_employe", args, OutilsRhService.Source.MCP))
                .willReturn(new ResultatOutil(false, "{\"id\":4}"));

        McpSchema.CallToolResult resultat = McpServerConfig.call(outils,
                McpTransportContext.create(Map.of(McpServerConfig.CLE_UTILISATEUR, karim)),
                new McpSchema.CallToolRequest("consulter_employe", args));

        assertThat(resultat.isError()).isFalse();
        assertThat(((McpSchema.TextContent) resultat.content().getFirst()).text()).isEqualTo("{\"id\":4}");
    }

    @Test
    @DisplayName("Appel MCP : une erreur d'outil devient un résultat isError (pas une erreur de protocole)")
    void call_shouldMapToolErrorToIsError() {
        given(outils.execute(any(), anyString(), anyMap(), any()))
                .willReturn(new ResultatOutil(true, "Accès refusé à cet employé"));

        McpSchema.CallToolResult resultat = McpServerConfig.call(outils,
                McpTransportContext.create(Map.of(McpServerConfig.CLE_UTILISATEUR, karim)),
                new McpSchema.CallToolRequest("consulter_employe", Map.of("employeId", 9)));

        assertThat(resultat.isError()).isTrue();
    }

    @Test
    @DisplayName("Appel MCP sans utilisateur : refusé, aucun outil exécuté")
    void call_shouldRefuse_withoutUser() {
        McpSchema.CallToolResult resultat = McpServerConfig.call(outils, McpTransportContext.EMPTY,
                new McpSchema.CallToolRequest("lister_departements", Map.of()));

        assertThat(resultat.isError()).isTrue();
        verify(outils, never()).execute(any(), anyString(), anyMap(), any());
    }

    @Test
    @DisplayName("Déclaration MCP : schéma, champs obligatoires et annotation « lecture seule » pour chaque outil")
    void specifications_shouldDeclareSchemaAndReadOnlyHint() {
        given(outils.definitions()).willReturn(new OutilsRhService(null, null, null, null, null, null, null).definitions());

        var specs = McpServerConfig.specifications(outils);

        assertThat(specs).hasSize(7);
        McpSchema.Tool absences = specs.stream().map(s -> s.tool())
                .filter(t -> t.name().equals("absences_periode")).findFirst().orElseThrow();
        assertThat(absences.inputSchema()).containsEntry("type", "object").containsEntry("required", List.of("du", "au"));
        assertThat(absences.annotations().readOnlyHint()).isTrue();
        assertThat(absences.annotations().destructiveHint()).isFalse();
    }
}

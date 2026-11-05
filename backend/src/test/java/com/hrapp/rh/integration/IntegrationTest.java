package com.hrapp.rh.integration;

import com.hrapp.rh.utilisateur.ComptesTestInitializer;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.mysql.MySQLContainer;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static org.mockito.BDDMockito.given;

/**
 * Base des tests d'intégration : l'application complète sur un port aléatoire, une vraie base MySQL
 * démarrée dans Docker (Testcontainers), de vraies requêtes HTTP (filtre JWT, @PreAuthorize, JSON, servlet MCP).
 *
 * Isolation : avant chaque test, la base est vidée puis reconstruite par les migrations Flyway et les
 * comptes de test sont recréés. Chaque test part donc des données de départ connues (V2, V5) :
 * Amine (ADMIN, n°1), Sara (RH, n°2), Karim (MANAGER, n°3), Lina (EMPLOYE, n°4, équipe de Karim).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.clean-disabled=false",          // autorisé uniquement ici, pour remettre la base à zéro
        "spring.jpa.show-sql=false",
        "app.assistant.ollama.url=http://localhost:9",  // aucun modèle : on teste le comportement « indisponible »
        "app.notifications.email-enabled=true"
})
public abstract class IntegrationTest {

    /** Un seul conteneur pour tous les tests (démarré une fois, partagé par le contexte Spring mis en cache). */
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    static {
        MYSQL.start();
    }

    /** Aucun email réel : on vérifie seulement qu'ils sont envoyés. */
    @MockitoBean
    protected JavaMailSender mailSender;

    @LocalServerPort
    private int port;

    @Autowired
    private Flyway flyway;

    @Autowired
    private ComptesTestInitializer comptesTest;

    protected Api api;

    @BeforeEach
    void resetDatabase() {
        flyway.clean();
        flyway.migrate();
        comptesTest.run(null);
        given(mailSender.createMimeMessage()).willAnswer(invocation -> new MimeMessage((Session) null));
        api = new Api(baseUrl());
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    /**
     * Un lundi de l'an prochain : toujours dans le futur et dans une seule année,
     * quel que soit le jour où les tests sont lancés.
     */
    protected static LocalDate mondayNextYear(int semaine) {
        LocalDate debut = LocalDate.now().plusYears(1).withMonth(3).withDayOfMonth(1);
        return debut.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).plusWeeks(semaine);
    }
}

package com.hrapp.rh.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FluxNotificationRegistryTest {

    /** Faux flux : enregistre les envois, ou échoue comme un client déconnecté. */
    private static final class FauxFlux extends SseEmitter {
        final List<SseEventBuilder> envois = new ArrayList<>();
        boolean deconnecte;
        boolean termine;

        FauxFlux(long timeout) {
            super(timeout);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (deconnecte) {
                throw new IOException("Broken pipe");
            }
            envois.add(builder);
        }

        @Override
        public synchronized void complete() {
            termine = true;
            super.complete();
        }
    }

    private final List<FauxFlux> crees = new ArrayList<>();

    private final FluxNotificationRegistry registry = new FluxNotificationRegistry(15) {
        @Override
        SseEmitter createEmitter(long timeoutMs) {
            FauxFlux flux = new FauxFlux(timeoutMs);
            crees.add(flux);
            return flux;
        }
    };

    @Test
    @DisplayName("Le flux dure autant qu'un jeton d'accès (15 min)")
    void open_shouldUseAccessTokenLifetimeAsTimeout() {
        registry.open(4L);

        assertThat(crees.getFirst().getTimeout()).isEqualTo(15 * 60_000L);
    }

    @Test
    @DisplayName("Un événement part sur tous les onglets de l'employé, et seulement les siens")
    void send_shouldReachAllConnectionsOfThatEmployeeOnly() {
        registry.open(4L);
        registry.open(4L);
        registry.open(3L);

        registry.send(4L, "notification", Map.of("nombre", 1));

        assertThat(crees.get(0).envois).hasSize(1);
        assertThat(crees.get(1).envois).hasSize(1);
        assertThat(crees.get(2).envois).isEmpty();
    }

    @Test
    @DisplayName("Un client déconnecté est retiré dès l'envoi suivant, les autres continuent")
    void send_shouldDropDisconnectedClient_andKeepOthers() {
        registry.open(4L);
        registry.open(4L);
        crees.getFirst().deconnecte = true;

        registry.send(4L, "notification", Map.of("nombre", 1));

        assertThat(registry.countConnections(4L)).isEqualTo(1);
        assertThat(crees.get(1).envois).hasSize(1);
    }

    @Test
    @DisplayName("Au-delà de 5 onglets, la connexion la plus ancienne est fermée")
    void open_shouldCloseOldestConnection_beyondLimit() {
        for (int i = 0; i < FluxNotificationRegistry.MAX_CONNEXIONS_PAR_EMPLOYE + 1; i++) {
            registry.open(4L);
        }

        assertThat(registry.countConnections(4L)).isEqualTo(FluxNotificationRegistry.MAX_CONNEXIONS_PAR_EMPLOYE);
        assertThat(crees.getFirst().termine).isTrue();
        assertThat(crees.getLast().termine).isFalse();
    }

    @Test
    @DisplayName("Le battement de cœur atteint tout le monde et purge les connexions mortes")
    void heartbeat_shouldPingEveryone_andPurgeDeadConnections() {
        registry.open(4L);
        registry.open(3L);
        crees.get(1).deconnecte = true;

        registry.heartbeat();

        assertThat(crees.get(0).envois).hasSize(1);
        assertThat(registry.countConnections(3L)).isZero();
    }

    @Test
    @DisplayName("Envoyer à un employé sans connexion ne fait rien")
    void send_shouldDoNothing_whenEmployeeIsNotConnected() {
        registry.send(99L, "notification", Map.of());

        assertThat(registry.countConnections(99L)).isZero();
    }
}

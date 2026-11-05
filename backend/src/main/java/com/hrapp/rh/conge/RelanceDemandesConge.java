package com.hrapp.rh.conge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Transmet aux RH les demandes restées sans décision du manager au-delà du délai (app.leave.escalation-days).
 * Chaque demande n'est transmise qu'une fois (date_escalade) ; les RH et le manager sont prévenus.
 * À partir de là, les RH peuvent décider (voir RegleDecisionConge) : un manager absent ne bloque personne.
 */
@Slf4j
@Component
public class RelanceDemandesConge {

    private final DemandeCongeRepository repository;
    private final CongeProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RelanceDemandesConge(DemandeCongeRepository repository, CongeProperties properties,
                                ApplicationEventPublisher eventPublisher, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.leave.escalation-check-ms:3600000}", initialDelay = 60_000)
    @Transactional
    public int escalateOverdueRequests() {
        LocalDateTime maintenant = LocalDateTime.now(clock);
        List<DemandeConge> enRetard = repository.findByStatutAndDateEscaladeIsNullAndDateCreationLessThanEqual(
                StatutDemandeConge.EN_ATTENTE, maintenant.minusDays(properties.escalationDays()));
        for (DemandeConge demande : enRetard) {
            demande.setDateEscalade(maintenant);
            eventPublisher.publishEvent(new DemandeCongeEvenement(DemandeCongeEvenement.Type.ESCALADEE, demande));
        }
        if (!enRetard.isEmpty()) {
            log.info("{} demande(s) de congé sans décision depuis {} jours transmise(s) aux RH",
                    enRetard.size(), properties.escalationDays());
        }
        return enRetard.size();
    }
}

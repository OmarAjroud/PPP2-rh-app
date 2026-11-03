package com.hrapp.rh.audit;

import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.Type;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Journalise automatiquement les entités marquées @Audited.
 * S'exécute pendant la transaction : la ligne d'audit est enregistrée avec la modification, ou pas du tout.
 */
@Component
@RequiredArgsConstructor
public class AuditEntityListener
        implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private final AuditService auditService;

    @Override
    public void onPostInsert(PostInsertEvent event) {
        Class<?> type = Hibernate.getClass(event.getEntity());
        Audited config = type.getAnnotation(Audited.class);
        if (config == null) {
            return;
        }
        Map<String, Object> valeurs = snapshot(event.getPersister(), event.getState(), event.getSession(), config);
        auditService.recordChange(AuditAction.CREATION, type.getSimpleName(), event.getId(), null, valeurs);
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        Class<?> type = Hibernate.getClass(event.getEntity());
        Audited config = type.getAnnotation(Audited.class);
        if (config == null || event.getOldState() == null) {
            return;
        }
        Map<String, Object> avant = snapshot(event.getPersister(), event.getOldState(), event.getSession(), config);
        Map<String, Object> apres = snapshot(event.getPersister(), event.getState(), event.getSession(), config);

        AuditSnapshot.Diff diff = AuditSnapshot.diff(avant, apres);
        if (diff.isEmpty()) {
            return;                                           // rien de réellement modifié : pas de ligne
        }
        auditService.recordChange(AuditAction.MODIFICATION, type.getSimpleName(), event.getId(),
                diff.anciennes(), diff.nouvelles());
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        Class<?> type = Hibernate.getClass(event.getEntity());
        Audited config = type.getAnnotation(Audited.class);
        if (config == null) {
            return;
        }
        Map<String, Object> valeurs = snapshot(event.getPersister(), event.getDeletedState(), event.getSession(), config);
        auditService.recordChange(AuditAction.SUPPRESSION, type.getSimpleName(), event.getId(), valeurs, null);
    }

    /** Pas de traitement après commit : tout se passe dans la transaction. (Pas de @Override : selon la version d'Hibernate.) */
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    /** Transforme l'état Hibernate en valeurs simples : une relation devient son identifiant (departementId : 3). */
    private Map<String, Object> snapshot(EntityPersister persister, Object[] state,
                                         SharedSessionContractImplementor session, Audited config) {
        Set<String> ignorees = Set.of(config.ignore());
        String[] noms = persister.getPropertyNames();
        Type[] types = persister.getPropertyTypes();

        Map<String, Object> valeurs = new LinkedHashMap<>();
        for (int i = 0; i < noms.length; i++) {
            if (ignorees.contains(noms[i]) || types[i].isCollectionType()) {
                continue;
            }
            Object valeur = state[i];
            if (types[i].isEntityType()) {
                valeurs.put(noms[i] + "Id", valeur == null ? null : session.getContextEntityIdentifier(valeur));
            } else {
                valeurs.put(noms[i], valeur);
            }
        }
        return valeurs;
    }
}

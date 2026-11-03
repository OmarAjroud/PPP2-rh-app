package com.hrapp.rh.conge;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/** Filtres dynamiques pour la recherche de demandes de congé. */
public final class DemandeCongeSpecification {

    private DemandeCongeSpecification() {}

    public static Specification<DemandeConge> employe(Long employeId) {
        return (root, query, cb) -> employeId == null
                ? cb.conjunction()
                : cb.equal(root.get("employe").get("id"), employeId);
    }

    public static Specification<DemandeConge> statut(StatutDemandeConge statut) {
        return (root, query, cb) -> statut == null
                ? cb.conjunction()
                : cb.equal(root.get("statut"), statut);
    }

    /** Demandes dont le début tombe dans l'année (une demande ne chevauche jamais deux années). */
    public static Specification<DemandeConge> annee(Integer annee) {
        return (root, query, cb) -> annee == null
                ? cb.conjunction()
                : cb.between(root.<LocalDate>get("dateDebut"), LocalDate.of(annee, 1, 1), LocalDate.of(annee, 12, 31));
    }
}
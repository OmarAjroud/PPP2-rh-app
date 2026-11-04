package com.hrapp.rh.conge;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import jakarta.persistence.criteria.Predicate;

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


    public static Specification<DemandeConge> departement(Long departementId) {
        return (root, query, cb) -> departementId == null
                ? cb.conjunction()
                : cb.equal(root.get("employe").get("departement").get("id"), departementId);
    }

    /** Demandes qui chevauchent la période [du, au] (chaque borne est optionnelle). */
    public static Specification<DemandeConge> chevauche(LocalDate du, LocalDate au) {
        return (root, query, cb) -> {
            Predicate predicat = cb.conjunction();
            if (au != null) {
                predicat = cb.and(predicat, cb.lessThanOrEqualTo(root.<LocalDate>get("dateDebut"), au));
            }
            if (du != null) {
                predicat = cb.and(predicat, cb.greaterThanOrEqualTo(root.<LocalDate>get("dateFin"), du));
            }
            return predicat;
        };
    }

    /** Demandes de l'équipe d'un manager : les employés dont manager_id est lui. */
    public static Specification<DemandeConge> equipeDe(Long managerEmployeId) {
        return (root, query, cb) -> cb.equal(root.get("employe").get("manager").get("id"), managerEmployeId);
    }
}
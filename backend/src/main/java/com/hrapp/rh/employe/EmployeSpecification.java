package com.hrapp.rh.employe;

import org.springframework.data.jpa.domain.Specification;

/** Filtres dynamiques pour la recherche d'employés. */
public final class EmployeSpecification {

    private EmployeSpecification() {}

    /** Recherche texte (insensible à la casse) sur le nom, le prénom et l'email. */
    public static Specification<Employe> recherche(String texte) {
        return (root, query, cb) -> {
            if (texte == null || texte.isBlank()) {
                return cb.conjunction();   // « toujours vrai » : pas de filtre
            }
            String motif = "%" + texte.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("nom")), motif),
                    cb.like(cb.lower(root.get("prenom")), motif),
                    cb.like(cb.lower(root.get("email")), motif));
        };
    }

    public static Specification<Employe> departement(Long departementId) {
        return (root, query, cb) -> departementId == null
                ? cb.conjunction()
                : cb.equal(root.get("departement").get("id"), departementId);
    }

    public static Specification<Employe> role(Long roleId) {
        return (root, query, cb) -> roleId == null
                ? cb.conjunction()
                : cb.equal(root.get("role").get("id"), roleId);
    }
}
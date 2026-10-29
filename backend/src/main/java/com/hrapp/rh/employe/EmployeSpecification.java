package com.hrapp.rh.employe;

import org.springframework.data.jpa.domain.Specification;

/** Filtres dynamiques pour la recherche d'employés. */
public final class EmployeSpecification {

    private EmployeSpecification() {}

    /** Recherche texte (insensible à la casse) sur le nom, le prénom et l'email. */
    public static Specification<Employe> search(String texte) {
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

    public static Specification<Employe> inDepartement(Long departementId) {
        return (root, query, cb) -> departementId == null
                ? cb.conjunction()
                : cb.equal(root.get("departement").get("id"), departementId);
    }

    public static Specification<Employe> hasRole(Long roleId) {
        return (root, query, cb) -> roleId == null
                ? cb.conjunction()
                : cb.equal(root.get("role").get("id"), roleId);
    }

    public static Specification<Employe> hasId(Long id) {
        return (root, query, cb) -> cb.equal(root.get("id"), id);
    }
}

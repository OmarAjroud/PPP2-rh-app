package com.hrapp.rh.employe;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;

/** Filtres dynamiques pour la recherche d'employés. */
public final class EmployeSpecification {

    private EmployeSpecification() {}

    /**
     * Recherche texte (insensible à la casse) sur le nom, le prénom et l'email.
     * Plusieurs mots : chacun doit apparaître dans l'un des champs, donc « Lina Gharbi » et
     * « gharbi lina » trouvent Lina Gharbi (le texte entier n'est dans aucun champ à lui seul).
     */
    public static Specification<Employe> search(String texte) {
        return (root, query, cb) -> {
            if (texte == null || texte.isBlank()) {
                return cb.conjunction();   // « toujours vrai » : pas de filtre
            }
            Predicate[] mots = Arrays.stream(texte.trim().toLowerCase().split("\\s+"))
                    .map(mot -> "%" + mot + "%")
                    .map(motif -> cb.or(
                            cb.like(cb.lower(root.get("nom")), motif),
                            cb.like(cb.lower(root.get("prenom")), motif),
                            cb.like(cb.lower(root.get("email")), motif)))
                    .toArray(Predicate[]::new);
            return cb.and(mots);
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

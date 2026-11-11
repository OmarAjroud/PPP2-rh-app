package com.hrapp.rh.employe;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Employés de démonstration (fictifs) : une petite entreprise tunisienne avec une vraie hiérarchie,
 * pour l'organigramme, le tableau de bord et les listes. Activé par app.seed.demo-employees
 * (SEED_DEMO_EMPLOYEES), jamais en production par défaut.
 *
 * Idempotent : chaque personne n'est ajoutée que si son email est absent ; un redémarrage n'ajoute rien.
 * Écrit en SQL (comme les données de départ V2) : pas d'entrées « système » dans le journal d'audit.
 * Sans compte utilisateur : ces personnes n'ont pas d'accès à l'application.
 * Tout ou rien (une transaction) ; une personne dont le département, le rôle ou le manager manque est ignorée.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoEmployesInitializer implements ApplicationRunner {

    /** Nombre de personnes ajoutées (en plus des 4 de départ). */
    public static final int NOMBRE = 15;

    private record Personne(String prenom, String nom, String email, String telephone, LocalDate embauche,
                            String salaire, String departement, String role, String managerEmail) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Value("${app.seed.demo-employees:false}")
    private boolean actif;

    /**
     * Ordre important : un manager est toujours ajouté avant son équipe.
     * Deux embauches récentes, datées par rapport à aujourd'hui : le tableau de bord montre toujours des arrivées du mois.
     */
    private static List<Personne> personnes(LocalDate aujourdhui) {
        LocalDate ceMois = aujourdhui.withDayOfMonth(1);
        LocalDate moisDernier = aujourdhui.minusMonths(1).withDayOfMonth(13);
        return List.of(
                // Informatique, sous Karim (manager@rh.com)
                new Personne("Youssef", "Ben Ali", "youssef.benali@rh.com", "+216 23 114 562", LocalDate.of(2022, 4, 4), "2650", "Informatique", "EMPLOYE", "manager@rh.com"),
                new Personne("Hédi", "Zouari", "hedi.zouari@rh.com", "+216 55 302 918", LocalDate.of(2024, 2, 12), "2300", "Informatique", "EMPLOYE", "manager@rh.com"),
                new Personne("Amira", "Trabelsi", "amira.trabelsi@rh.com", "+216 98 771 340", LocalDate.of(2020, 9, 7), "3450", "Informatique", "MANAGER", "manager@rh.com"),
                new Personne("Rim", "Ayari", "rim.ayari@rh.com", "+216 29 640 115", LocalDate.of(2025, 1, 6), "1950", "Informatique", "EMPLOYE", "amira.trabelsi@rh.com"),
                new Personne("Walid", "Chebbi", "walid.chebbi@rh.com", "+216 50 218 774", LocalDate.of(2023, 6, 19), "2100", "Informatique", "EMPLOYE", "amira.trabelsi@rh.com"),
                // Finance, sous Mehdi, rattaché à la direction
                new Personne("Mehdi", "Kacem", "mehdi.kacem@rh.com", "+216 97 405 263", LocalDate.of(2019, 3, 11), "4100", "Finance", "MANAGER", "admin@rh.com"),
                new Personne("Sami", "Haddad", "sami.haddad@rh.com", "+216 21 856 409", LocalDate.of(2021, 11, 2), "2480", "Finance", "EMPLOYE", "mehdi.kacem@rh.com"),
                new Personne("Leila", "Bouazizi", "leila.bouazizi@rh.com", "+216 92 337 581", LocalDate.of(2023, 1, 16), "2250", "Finance", "EMPLOYE", "mehdi.kacem@rh.com"),
                new Personne("Ines", "Ferchichi", "ines.ferchichi@rh.com", "+216 54 690 127", ceMois, "1800", "Finance", "EMPLOYE", "mehdi.kacem@rh.com"),
                // Commercial, sous Sonia, rattachée à la direction
                new Personne("Sonia", "Gharsalli", "sonia.gharsalli@rh.com", "+216 99 128 456", LocalDate.of(2020, 5, 25), "3900", "Commercial", "MANAGER", "admin@rh.com"),
                new Personne("Anis", "Khelifi", "anis.khelifi@rh.com", "+216 26 471 903", LocalDate.of(2022, 10, 10), "2200", "Commercial", "EMPLOYE", "sonia.gharsalli@rh.com"),
                new Personne("Mariem", "Saidi", "mariem.saidi@rh.com", "+216 53 815 270", LocalDate.of(2024, 9, 2), "2050", "Commercial", "EMPLOYE", "sonia.gharsalli@rh.com"),
                new Personne("Bilel", "Hamdi", "bilel.hamdi@rh.com", "+216 94 263 718", moisDernier, "1750", "Commercial", "EMPLOYE", "sonia.gharsalli@rh.com"),
                // Ressources humaines, sous Sara (rh@rh.com)
                new Personne("Nour", "Mansour", "nour.mansour@rh.com", "+216 28 940 362", LocalDate.of(2023, 3, 6), "1900", "Ressources Humaines", "EMPLOYE", "rh@rh.com"),
                new Personne("Olfa", "Jaziri", "olfa.jaziri@rh.com", "+216 52 774 019", LocalDate.of(2025, 6, 2), "1850", "Ressources Humaines", "EMPLOYE", "rh@rh.com"));
    }

    /** Called by Spring through the proxy at start-up: the whole seeding is one transaction. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (actif) {
            ajouter();
        }
    }

    /** Ajoute ce qui manque (département Commercial, personnes, rattachement des responsables à la direction). */
    @Transactional
    public void ajouter() {
        if (id("select id from departements where nom = ?", "Commercial") == null) {
            jdbc.update("insert into departements (nom, description) values ('Commercial', 'Ventes et relation client')");
        }
        // the HR manager and the IT manager report to the director
        Long direction = idEmploye("admin@rh.com");
        if (direction != null) {
            jdbc.update("update employes set manager_id = ? where email in ('rh@rh.com', 'manager@rh.com') and manager_id is null", direction);
        }

        int ajoutees = 0;
        for (Personne p : personnes(LocalDate.now(clock))) {
            if (idEmploye(p.email()) != null) {
                continue;
            }
            Long departement = id("select id from departements where nom = ?", p.departement());
            Long role = id("select id from roles where nom = ?", p.role());
            Long manager = idEmploye(p.managerEmail());
            if (departement == null || role == null || manager == null) {
                // a renamed department or role in this database: skip this person rather than block start-up
                log.warn("Employé de démonstration ignoré ({}) : département, rôle ou manager introuvable", p.email());
                continue;
            }
            jdbc.update("""
                    insert into employes (nom, prenom, email, telephone, date_embauche, salaire, departement_id, role_id, manager_id)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, p.nom(), p.prenom(), p.email(), p.telephone(), p.embauche(), new BigDecimal(p.salaire()),
                    departement, role, manager);
            ajoutees++;
        }
        if (ajoutees > 0) {
            log.info("Employés de démonstration ajoutés : {}", ajoutees);
        }
    }

    private Long idEmploye(String email) {
        return id("select id from employes where email = ?", email);
    }

    private Long id(String requete, String valeur) {
        List<Long> ids = jdbc.queryForList(requete, Long.class, valeur);
        return ids.isEmpty() ? null : ids.getFirst();
    }
}

package com.hrapp.rh.integration;

import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Exports Excel et PDF : mêmes droits et mêmes filtres que les listes, et chaque export est tracé. */
class ExportsIT extends IntegrationTest {

    private static List<Row> lignes(byte[] xlsx) throws Exception {
        try (ReadableWorkbook classeur = new ReadableWorkbook(new ByteArrayInputStream(xlsx))) {
            return classeur.getFirstSheet().read();
        }
    }

    @Test
    @DisplayName("RH : la liste des employés en Excel, avec un nom de fichier daté et une ligne par employé")
    void hr_shouldExportEmployeesToExcel() throws Exception {
        String rh = api.login("rh");
        Api.Fichier f = api.telecharger("/api/exports/employes?format=xlsx", rh);

        assertThat(f.statut()).isEqualTo(200);
        assertThat(f.type()).startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(f.disposition()).contains("attachment").contains("employes-" + LocalDate.now() + ".xlsx");
        long total = api.get("/api/employes?size=1", rh).get("totalElements").asLong();
        List<Row> lignes = lignes(f.corps());
        assertThat(lignes).hasSize((int) total + 1);   // + en-têtes
        assertThat(lignes.get(0).getCell(0).asString()).isEqualTo("ID");
        // roles in French, as on screen
        assertThat(lignes.stream().skip(1).map(l -> l.getCell(5).asString())).contains("Administrateur", "Ressources humaines", "Manager", "Employé");
        // never a salary: no such column, and no salary value of the seed data anywhere in the file
        assertThat(lignes.get(0).stream().map(c -> c.getText())).noneMatch(t -> t.toLowerCase().contains("salaire"));
        String texte = lignes.stream().flatMap(Row::stream).map(c -> c == null ? "" : c.getText()).reduce("", String::concat);
        for (var e : api.get("/api/employes?size=100", rh).get("contenu")) {
            var salaire = api.get("/api/employes/" + e.get("id").asLong(), rh).get("salaire");
            if (!salaire.isMissingNode() && !salaire.isNull()) {
                assertThat(texte).doesNotContain(salaire.asString().replaceAll("\\.0+$", ""));
            }
        }
    }

    @Test
    @DisplayName("Mêmes droits que la liste : un manager n'exporte que ce qu'il voit, un employé que lui-même")
    void export_shouldFollowListPermissions() throws Exception {
        String manager = api.login("manager");
        long visibles = api.get("/api/employes?size=1", manager).get("totalElements").asLong();
        assertThat(lignes(api.telecharger("/api/exports/employes?format=xlsx", manager).corps())).hasSize((int) visibles + 1);

        String employe = api.login("employe");
        List<Row> siens = lignes(api.telecharger("/api/exports/employes?format=xlsx", employe).corps());
        assertThat(siens).hasSize(2);
        assertThat(siens.get(1).getCell(3).asString()).isEqualTo(api.get("/api/employes/me", employe).get("email").asString());

        // another department in the filter never widens what a manager sees
        long autre = api.get("/api/departements", manager).json().valueStream()
                .filter(d -> !"Informatique".equals(d.get("nom").asString())).findFirst().orElseThrow().get("id").asLong();
        assertThat(lignes(api.telecharger("/api/exports/employes?format=xlsx&departementId=" + autre, manager).corps())).hasSize(1);
    }

    @Test
    @DisplayName("Les filtres de l'écran s'appliquent à l'export")
    void export_shouldApplyFilters() throws Exception {
        String rh = api.login("rh");
        long filtres = api.get("/api/employes?size=1&recherche=lina", rh).get("totalElements").asLong();
        assertThat(filtres).isPositive();
        assertThat(lignes(api.telecharger("/api/exports/employes?format=xlsx&recherche=lina", rh).corps())).hasSize((int) filtres + 1);
    }

    @Test
    @DisplayName("Demandes de congé (RH) en PDF ; refusé à un employé et à un manager")
    void leaveRequests_shouldBeExportedForHrOnly() {
        Api.Fichier pdf = api.telecharger("/api/exports/demandes?format=pdf&statut=EN_ATTENTE", api.login("rh"));
        assertThat(pdf.statut()).isEqualTo(200);
        assertThat(pdf.type()).startsWith("application/pdf");
        assertThat(new String(pdf.corps(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        assertThat(api.telecharger("/api/exports/demandes?format=pdf", api.login("employe")).statut()).isEqualTo(403);
        assertThat(api.telecharger("/api/exports/demandes?format=pdf", api.login("manager")).statut()).isEqualTo(403);
    }

    @Test
    @DisplayName("Journal d'audit : exporté par l'administrateur seulement")
    void audit_shouldBeExportedByAdminOnly() {
        assertThat(api.telecharger("/api/exports/audit?format=xlsx", api.login("admin")).statut()).isEqualTo(200);
        assertThat(api.telecharger("/api/exports/audit?format=xlsx", api.login("rh")).statut()).isEqualTo(403);
    }

    @Test
    @DisplayName("Départements : exportables par tous ; format inconnu refusé")
    void departments_andUnknownFormat() throws Exception {
        String employe = api.login("employe");
        long departements = api.get("/api/departements", employe).json().size();
        assertThat(lignes(api.telecharger("/api/exports/departements?format=xlsx", employe).corps())).hasSize((int) departements + 1);
        assertThat(api.telecharger("/api/exports/departements?format=csv", employe).statut()).isEqualTo(400);
        assertThat(api.telecharger("/api/exports/inconnue?format=xlsx", employe).statut()).isEqualTo(404);
    }

    @Test
    @DisplayName("Chaque export est tracé dans le journal d'audit (qui, quelle liste, quel format, combien de lignes)")
    void export_shouldBeAudited() {
        api.telecharger("/api/exports/employes?format=pdf&recherche=lina", api.login("rh"));
        var journal = api.get("/api/admin/audit?action=EXPORT", api.login("admin"));
        assertThat(journal.get("totalElements").asLong()).isEqualTo(1);
        var entree = journal.get("contenu").get(0);
        assertThat(entree.get("nomUtilisateur").asString()).isEqualTo("rh");
        assertThat(entree.get("entiteId").asString()).isEqualTo("employes");
        assertThat(entree.get("details").asString()).contains("pdf").contains("lina");
    }
}

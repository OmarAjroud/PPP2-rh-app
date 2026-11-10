package com.hrapp.rh.export;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.CellType;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ExportFichiersTest {

    private static final LocalDateTime GENERE_LE = LocalDateTime.of(2026, 11, 11, 9, 30);

    private static Tableau tableau(int lignesEnPlus) {
        List<List<Object>> lignes = new ArrayList<>();
        lignes.add(List.of(1L, "Hélène Dupré", LocalDate.of(2026, 12, 14), new BigDecimal("2.5"), "=1+1"));
        lignes.add(java.util.Arrays.asList(2L, "Karim Ben Ali", null, BigDecimal.ONE, "@SOMME(A1)"));
        IntStream.range(0, lignesEnPlus).forEach(i -> lignes.add(List.of(3L + i, "Personne " + i, LocalDate.of(2026, 1, 1), BigDecimal.TEN, "-")));
        return new Tableau("Demandes de congé", "Statut : En attente", List.of(
                new Colonne("N°", Colonne.Type.NOMBRE, 8),
                new Colonne("Employé", Colonne.Type.TEXTE, 28),
                new Colonne("Début", Colonne.Type.DATE, 12),
                new Colonne("Jours", Colonne.Type.NOMBRE, 8),
                new Colonne("Commentaire", Colonne.Type.TEXTE, 30)), lignes);
    }

    @Test
    @DisplayName("Excel : en-têtes, nombres et dates typés, une valeur qui ressemble à une formule reste du texte")
    void excel_shouldWriteTypedCellsAndNeverFormulas() throws Exception {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        ExportExcel.ecrire(tableau(0), sortie);

        try (ReadableWorkbook classeur = new ReadableWorkbook(new ByteArrayInputStream(sortie.toByteArray()))) {
            var feuille = classeur.getFirstSheet();
            assertThat(feuille.getName()).isEqualTo("Demandes de congé");
            List<Row> lignes = feuille.read();
            assertThat(lignes).hasSize(3);
            assertThat(lignes.get(0).getCell(1).asString()).isEqualTo("Employé");

            Row premiere = lignes.get(1);
            assertThat(premiere.getCell(1).asString()).isEqualTo("Hélène Dupré");
            assertThat(premiere.getCell(0).getType()).isEqualTo(CellType.NUMBER);
            assertThat(premiere.getCell(2).asDate().toLocalDate()).isEqualTo(LocalDate.of(2026, 12, 14));
            assertThat(premiere.getCell(3).asNumber()).isEqualByComparingTo("2.5");
            Cell formule = premiere.getCell(4);
            assertThat(formule.getType()).isEqualTo(CellType.STRING);   // jamais exécutée par le tableur
            assertThat(formule.asString()).isEqualTo("=1+1");

            Cell vide = lignes.get(2).getCell(2);
            assertThat(vide == null || vide.getType() == CellType.EMPTY).isTrue();
        }
    }

    @Test
    @DisplayName("Excel : le nom de la feuille est nettoyé (31 caractères, sans caractères interdits)")
    void excel_shouldSanitizeSheetName() throws Exception {
        Tableau t = new Tableau("Journal d'audit : 01/11/2026 – 30/11/2026 [admin]", null,
                List.of(new Colonne("A", Colonne.Type.TEXTE, 10)), List.of());
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        ExportExcel.ecrire(t, sortie);
        try (ReadableWorkbook classeur = new ReadableWorkbook(new ByteArrayInputStream(sortie.toByteArray()))) {
            String nom = classeur.getFirstSheet().getName();
            assertThat(nom).hasSizeLessThanOrEqualTo(31).doesNotContain(":", "/", "[", "]");
        }
    }

    @Test
    @DisplayName("PDF : titre, filtres, auteur, valeurs accentuées et numéros de page « Page x / y »")
    void pdf_shouldContainTitleValuesAndPageNumbers() throws Exception {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        ExportPdf.ecrire(tableau(80), "sara.rh", GENERE_LE, sortie);
        byte[] octets = sortie.toByteArray();

        assertThat(new String(octets, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        PdfReader lecteur = new PdfReader(octets);
        try {
            assertThat(lecteur.getNumberOfPages()).isGreaterThan(1);
            PdfTextExtractor extracteur = new PdfTextExtractor(lecteur);
            String page1 = extracteur.getTextFromPage(1);
            assertThat(page1).contains("Demandes de congé", "Statut : En attente", "sara.rh", "11/11/2026", "Hélène Dupré", "14/12/2026", "2,5");
            // the total is drawn in a template shared by every page: the extractor returns it as a separate piece of text
            assertThat(page1.replaceAll("\\s+", " ")).contains("Page 1 / " + lecteur.getNumberOfPages());
            // the header row is repeated on every page
            assertThat(extracteur.getTextFromPage(2)).contains("Employé", "Commentaire");
        } finally {
            lecteur.close();
        }
    }

    @Test
    @DisplayName("PDF : une liste vide le dit au lieu d'un tableau sans ligne")
    void pdf_shouldSayWhenEmpty() throws Exception {
        Tableau vide = new Tableau("Employés", null, List.of(new Colonne("Nom", Colonne.Type.TEXTE, 20)), List.of());
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        ExportPdf.ecrire(vide, "rh", GENERE_LE, sortie);
        PdfReader lecteur = new PdfReader(sortie.toByteArray());
        try {
            assertThat(new PdfTextExtractor(lecteur).getTextFromPage(1)).contains("Aucune ligne à exporter");
        } finally {
            lecteur.close();
        }
    }

    @Test
    @DisplayName("PDF : un long tableau écrit par blocs garde toutes ses lignes et son en-tête sur chaque page")
    void pdf_shouldWriteLongTablesInBlocks() throws Exception {
        int lignes = ExportPdf.BLOC * 2 + 37;   // crosses two block boundaries
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        ExportPdf.ecrire(tableau(lignes), "rh", GENERE_LE, sortie);
        PdfReader lecteur = new PdfReader(sortie.toByteArray());
        try {
            PdfTextExtractor extracteur = new PdfTextExtractor(lecteur);
            StringBuilder tout = new StringBuilder();
            for (int p = 1; p <= lecteur.getNumberOfPages(); p++) {
                String page = extracteur.getTextFromPage(p);
                assertThat(page).as("page %d", p).contains("Employé", "Commentaire");
                tout.append(page).append('\n');
            }
            for (int i : new int[] { 0, ExportPdf.BLOC - 3, ExportPdf.BLOC - 2, ExportPdf.BLOC * 2 - 2, lignes - 1 }) {
                assertThat(tout.toString()).contains("Personne " + i + "01/01/2026");
            }
        } finally {
            lecteur.close();
        }
    }

}

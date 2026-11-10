package com.hrapp.rh.export;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Écrit un {@link Tableau} en PDF avec OpenPDF : A4 paysage, titre, filtres appliqués, auteur et date,
 * en-tête du tableau répété sur chaque page, « Page x / y » en bas.
 * Polices standard (Helvetica, codage WinAnsi) : les accents français et l'apostrophe typographique passent.
 */
public final class ExportPdf {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_HEURE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color ENTETE = new Color(0xE8, 0xE8, 0xF0);
    private static final Color TRAIT = new Color(0xD4, 0xD4, 0xDC);
    private static final Color GRIS = new Color(0x6B, 0x6B, 0x76);

    private static final Font TITRE = new Font(Font.HELVETICA, 16, Font.BOLD);
    private static final Font INFO = new Font(Font.HELVETICA, 9, Font.NORMAL, GRIS);
    private static final Font TETE = new Font(Font.HELVETICA, 8, Font.BOLD);
    private static final Font CELLULE = new Font(Font.HELVETICA, 8, Font.NORMAL);

    private ExportPdf() {
    }

    public static void ecrire(Tableau tableau, String auteur, LocalDateTime genereLe, OutputStream sortie) throws IOException {
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 40);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, sortie);
            writer.setPageEvent(new NumerosDePage());
            document.addTitle(tableau.titre());
            document.addCreator("RH App");
            document.open();

            document.add(new Paragraph(tableau.titre(), TITRE));
            String info = "Généré le " + genereLe.format(DATE_HEURE).replace(" ", " à ") + " par " + auteur
                    + " · " + tableau.lignes().size() + (tableau.lignes().size() > 1 ? " lignes" : " ligne");
            document.add(new Paragraph(info, INFO));
            if (tableau.filtres() != null && !tableau.filtres().isBlank()) {
                document.add(new Paragraph(tableau.filtres(), INFO));
            }
            document.add(Chunk.NEWLINE);

            if (tableau.lignes().isEmpty()) {
                boolean filtre = tableau.filtres() != null && !tableau.filtres().isBlank();
                document.add(new Paragraph(filtre ? "Aucune ligne ne correspond à ces critères." : "Aucune ligne à exporter.", CELLULE));
            } else {
                document.add(table(tableau));
            }
        } catch (DocumentException e) {
            throw new IOException("Génération du PDF impossible", e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
    }

    private static PdfPTable table(Tableau tableau) {
        List<Colonne> colonnes = tableau.colonnes();
        PdfPTable table = new PdfPTable(colonnes.size());
        table.setWidthPercentage(100);
        table.setWidths(colonnes.stream().mapToInt(Colonne::largeur).toArray());
        table.setHeaderRows(1);   // repeated on every page

        for (Colonne c : colonnes) {
            PdfPCell cellule = cellule(c.libelle(), TETE, c.type());
            cellule.setBackgroundColor(ENTETE);
            table.addCell(cellule);
        }
        for (List<Object> ligne : tableau.lignes()) {
            for (int i = 0; i < colonnes.size(); i++) {
                Colonne c = colonnes.get(i);
                table.addCell(cellule(texte(i < ligne.size() ? ligne.get(i) : null), CELLULE, c.type()));
            }
        }
        return table;
    }

    private static PdfPCell cellule(String texte, Font police, Colonne.Type type) {
        PdfPCell cellule = new PdfPCell(new Phrase(texte, police));
        cellule.setPadding(4);
        cellule.setBorderColor(TRAIT);
        cellule.setHorizontalAlignment(type == Colonne.Type.NOMBRE ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        return cellule;
    }

    /** Valeur → texte français : "2,5", "14/12/2026", "14/12/2026 09:30". */
    static String texte(Object valeur) {
        return switch (valeur) {
            case null -> "";
            case LocalDate d -> d.format(DATE);
            case LocalDateTime d -> d.format(DATE_HEURE);
            case BigDecimal n -> nombre().format(n);
            case Double n -> nombre().format(n);
            case Float n -> nombre().format(n);
            case Number n -> String.valueOf(n);
            default -> valeur.toString();
        };
    }

    private static DecimalFormat nombre() {
        DecimalFormat f = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.FRANCE));
        f.setGroupingUsed(false);
        return f;
    }

    /** « Page x / y » : y n'est connu qu'à la fin, il est écrit dans un gabarit commun à toutes les pages. */
    private static final class NumerosDePage extends PdfPageEventHelper {
        private PdfTemplate total;
        private BaseFont police;

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(40, 12);
            try {
                police = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (DocumentException | IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            String texte = "Page " + writer.getPageNumber() + " / ";
            Rectangle page = document.getPageSize();
            float largeur = police.getWidthPoint(texte, 8);
            float x = page.getRight() - document.rightMargin() - largeur - 20;
            float y = page.getBottom() + 20;
            cb.beginText();
            cb.setFontAndSize(police, 8);
            cb.setColorFill(GRIS);
            cb.setTextMatrix(x, y);
            cb.showText(texte);
            cb.endText();
            cb.addTemplate(total, x + largeur, y);
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase("RH App", INFO), document.leftMargin(), y, 0);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(police, 8);
            total.setColorFill(GRIS);
            total.setTextMatrix(0, 0);
            total.showText(String.valueOf(writer.getPageNumber() - 1));
            total.endText();
        }
    }
}

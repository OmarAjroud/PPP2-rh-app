package com.hrapp.rh.export;

import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Écrit un {@link Tableau} en Excel (.xlsx) avec fastexcel : en-tête en gras figé, filtre automatique,
 * nombres et dates typés (triables, calculables). Le texte est toujours écrit comme du texte : une valeur
 * « =... » saisie par un utilisateur n'est jamais exécutée comme une formule par le tableur.
 */
public final class ExportExcel {

    /** Excel : 31 caractères au plus, sans : \ / ? * [ ] */
    private static final int NOM_FEUILLE_MAX = 31;

    private ExportExcel() {
    }

    public static void ecrire(Tableau tableau, OutputStream sortie) throws IOException {
        Workbook classeur = new Workbook(sortie, "RH App", "1.0");
        Worksheet feuille = classeur.newWorksheet(nomFeuille(tableau.titre()));
        List<Colonne> colonnes = tableau.colonnes();

        for (int c = 0; c < colonnes.size(); c++) {
            feuille.value(0, c, colonnes.get(c).libelle());
            feuille.width(c, colonnes.get(c).largeur());
        }
        feuille.range(0, 0, 0, colonnes.size() - 1).style().bold().fillColor("E8E8F0").set();
        feuille.freezePane(0, 1);

        int r = 1;
        for (List<Object> ligne : tableau.lignes()) {
            for (int c = 0; c < colonnes.size(); c++) {
                ecrireCellule(feuille, r, c, colonnes.get(c).type(), c < ligne.size() ? ligne.get(c) : null);
            }
            r++;
        }
        if (!tableau.lignes().isEmpty()) {
            feuille.setAutoFilter(0, 0, colonnes.size() - 1);
        }
        classeur.finish();
    }

    private static void ecrireCellule(Worksheet feuille, int r, int c, Colonne.Type type, Object valeur) {
        switch (valeur) {
            case null -> { }
            case Number n -> feuille.value(r, c, n);
            case LocalDate d -> {
                feuille.value(r, c, d);
                feuille.style(r, c).format("dd/mm/yyyy").set();
            }
            case LocalDateTime d -> {
                feuille.value(r, c, d);
                feuille.style(r, c).format("dd/mm/yyyy hh:mm").set();
            }
            default -> feuille.value(r, c, valeur.toString());
        }
    }

    static String nomFeuille(String titre) {
        String nom = titre == null || titre.isBlank() ? "Export" : titre.replaceAll("[:\\\\/?*\\[\\]]", " ").replaceAll("\\s+", " ").trim();
        return nom.length() > NOM_FEUILLE_MAX ? nom.substring(0, NOM_FEUILLE_MAX).trim() : nom;
    }
}

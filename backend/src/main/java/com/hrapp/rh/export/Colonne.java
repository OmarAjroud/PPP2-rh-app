package com.hrapp.rh.export;

/**
 * Une colonne d'un export.
 *
 * @param libelle en-tête affiché
 * @param type    façon d'écrire les valeurs (nombre et date restent typés dans Excel, pour trier et calculer)
 * @param largeur largeur indicative, en caractères (Excel) et en proportion (PDF)
 */
public record Colonne(String libelle, Type type, int largeur) {

    public enum Type { TEXTE, NOMBRE, DATE, DATE_HEURE }
}

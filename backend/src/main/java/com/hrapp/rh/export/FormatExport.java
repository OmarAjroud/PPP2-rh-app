package com.hrapp.rh.export;

import com.hrapp.rh.common.exception.RequeteInvalideException;
import org.springframework.http.MediaType;

import java.util.Locale;

/** Formats proposés : ?format=xlsx ou ?format=pdf. */
public enum FormatExport {

    XLSX("xlsx", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
    PDF("pdf", MediaType.APPLICATION_PDF);

    private final String extension;
    private final MediaType type;

    FormatExport(String extension, MediaType type) {
        this.extension = extension;
        this.type = type;
    }

    public String extension() {
        return extension;
    }

    public MediaType type() {
        return type;
    }

    public static FormatExport depuis(String valeur) {
        String v = valeur == null ? "" : valeur.trim().toLowerCase(Locale.ROOT);
        for (FormatExport f : values()) {
            if (f.extension.equals(v)) {
                return f;
            }
        }
        throw new RequeteInvalideException("Format d'export inconnu : " + valeur + " (xlsx ou pdf)");
    }
}

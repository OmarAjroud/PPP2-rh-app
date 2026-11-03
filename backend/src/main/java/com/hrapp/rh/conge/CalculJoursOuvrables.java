package com.hrapp.rh.conge;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/** Compte les jours ouvrables d'une période (week-ends et jours fériés exclus). */
public final class CalculJoursOuvrables {

    private CalculJoursOuvrables() {}

    public static int compter(LocalDate debut, LocalDate fin, Set<DayOfWeek> weekend, Set<LocalDate> joursFeries) {
        if (fin.isBefore(debut)) {
            throw new IllegalArgumentException("La date de fin précède la date de début");
        }
        int total = 0;
        for (LocalDate jour = debut; !jour.isAfter(fin); jour = jour.plusDays(1)) {
            if (!weekend.contains(jour.getDayOfWeek()) && !joursFeries.contains(jour)) {
                total++;
            }
        }
        return total;
    }
}
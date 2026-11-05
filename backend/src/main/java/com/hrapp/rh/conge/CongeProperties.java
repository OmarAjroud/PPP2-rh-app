package com.hrapp.rh.conge;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

/**
 * Paramètres des congés (section app.leave de application.yml).
 *
 * @param weekendDays     jours non ouvrables
 * @param escalationDays  jours sans décision du manager avant que la demande soit transmise aux RH
 */
@ConfigurationProperties(prefix = "app.leave")
public record CongeProperties(Set<DayOfWeek> weekendDays, Integer escalationDays) {

    public CongeProperties {
        weekendDays = (weekendDays == null || weekendDays.isEmpty())
                ? EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                : EnumSet.copyOf(weekendDays);
        // délai (en jours) au-delà duquel une demande sans décision du manager est transmise aux RH
        escalationDays = escalationDays == null || escalationDays < 1 ? 3 : escalationDays;
    }
}
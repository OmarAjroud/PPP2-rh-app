package com.hrapp.rh.conge;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

/** Paramètres des congés (section app.leave de application.yml). */
@ConfigurationProperties(prefix = "app.leave")
public record CongeProperties(Set<DayOfWeek> weekendDays) {

    public CongeProperties {
        weekendDays = (weekendDays == null || weekendDays.isEmpty())
                ? EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                : EnumSet.copyOf(weekendDays);
    }
}
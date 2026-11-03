package com.hrapp.rh.conge;

import java.time.LocalDate;

public record JoursOuvrablesResponse(LocalDate dateDebut, LocalDate dateFin,
                                     int joursOuvrables, long joursCalendaires) {}
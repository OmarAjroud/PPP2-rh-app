package com.hrapp.rh.conge;

import java.time.LocalDate;

public record JourFerieResponse(Long id, LocalDate date, String libelle) {}
package com.hrapp.rh.conge;

import java.math.BigDecimal;

public record TypeCongeResponse(Long id, String code, String libelle, boolean deduitDuSolde,
                                BigDecimal joursParAn, boolean retroactifAutorise) {}
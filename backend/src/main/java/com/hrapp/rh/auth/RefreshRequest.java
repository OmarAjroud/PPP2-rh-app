package com.hrapp.rh.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "Le jeton de rafraîchissement est obligatoire") String refreshToken
) {}
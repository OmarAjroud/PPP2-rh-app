package com.hrapp.rh.auth;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,          // durée de vie du jeton d'accès, en secondes
        ProfilResponse profil
) {}
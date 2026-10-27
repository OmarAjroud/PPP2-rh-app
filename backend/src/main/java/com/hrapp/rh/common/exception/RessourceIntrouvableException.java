package com.hrapp.rh.common.exception;

/** Ressource demandée inexistante (HTTP 404). */
public class RessourceIntrouvableException extends RuntimeException {
    public RessourceIntrouvableException(String message) {
        super(message);
    }
}
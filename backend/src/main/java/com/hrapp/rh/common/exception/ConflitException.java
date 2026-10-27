package com.hrapp.rh.common.exception;

/** Conflit métier : doublon, suppression interdite... (HTTP 409). */
public class ConflitException extends RuntimeException {
    public ConflitException(String message) {
        super(message);
    }
}
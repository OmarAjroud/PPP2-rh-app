package com.hrapp.rh.common.exception;

import java.time.Duration;

/** Trop de tentatives en peu de temps (HTTP 429), avec le délai avant de pouvoir réessayer. */
public class TropDeTentativesException extends RuntimeException {

    private final Duration attente;

    public TropDeTentativesException(String message, Duration attente) {
        super(message);
        this.attente = attente;
    }

    public Duration getAttente() {
        return attente;
    }
}

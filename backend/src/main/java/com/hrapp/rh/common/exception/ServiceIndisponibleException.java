package com.hrapp.rh.common.exception;

/** Service externe non configuré ou momentanément indisponible (HTTP 503). */
public class ServiceIndisponibleException extends RuntimeException {
    public ServiceIndisponibleException(String message) {
        super(message);
    }
}

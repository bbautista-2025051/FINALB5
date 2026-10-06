package com.veterinaria.citas.exception;

public class SolicitudInvalidaException extends RuntimeException {

    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
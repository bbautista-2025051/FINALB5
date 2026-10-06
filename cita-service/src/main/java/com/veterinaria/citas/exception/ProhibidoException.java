package com.veterinaria.citas.exception;

public class ProhibidoException extends RuntimeException {

    public ProhibidoException(String mensaje) {
        super(mensaje);
    }
}
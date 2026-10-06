package com.veterinaria.expedientes.exception;

public class ProhibidoException extends RuntimeException {

    public ProhibidoException(String mensaje) {
        super(mensaje);
    }
}

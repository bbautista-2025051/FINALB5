package com.veterinaria.usuarios.exception;

public class ProhibidoException extends RuntimeException {

    public ProhibidoException(String mensaje) {
        super(mensaje);
    }
}

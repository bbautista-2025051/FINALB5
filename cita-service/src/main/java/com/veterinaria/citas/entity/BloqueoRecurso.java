package com.veterinaria.citas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "bloqueo_recurso")
@Getter
@Setter
public class BloqueoRecurso {

    @Id
    @Column(name = "clave", nullable = false, length = 100)
    private String clave;

    protected BloqueoRecurso() {
    }

    public BloqueoRecurso(String clave) {
        this.clave = clave;
    }
}
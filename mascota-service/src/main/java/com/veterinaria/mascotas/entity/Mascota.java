package com.veterinaria.mascotas.entity;

import com.veterinaria.mascotas.enums.Especie;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

@Entity
@Table(
        name = "mascotas",
        indexes = {
                @Index(name = "idx_mascota_cliente", columnList = "cliente_id"),
                @Index(name = "idx_mascota_especie", columnList = "especie")
        }
)
@Getter
@Setter
public class Mascota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Especie especie;

    @Column(length = 100)
    private String raza;

    @Column(nullable = false)
    @Check(constraints = "edad >= 0")
    private int edad;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "cliente_nombre", nullable = false, length = 100)
    private String clienteNombre;
}

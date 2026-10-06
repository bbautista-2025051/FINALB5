package com.veterinaria.citas.entity;

import com.veterinaria.citas.enums.EstadoCita;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "citas",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_cita_vet_fecha",
                columnNames = {"veterinario_id", "fecha_hora"}
        ),
        indexes = {
                @Index(name = "idx_cita_fecha", columnList = "fecha_hora"),
                @Index(name = "idx_cita_mascota", columnList = "mascota_id"),
                @Index(name = "idx_cita_cliente_fecha", columnList = "cliente_id, fecha_hora")
        }
)
@Getter
@Setter
public class CitaMedica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mascota_id", nullable = false)
    private Long mascotaId;

    @Column(name = "mascota_nombre", nullable = false, length = 100)
    private String mascotaNombre;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "veterinario_id", nullable = false)
    private Long veterinarioId;

    @Column(name = "veterinario_nombre", nullable = false, length = 100)
    private String veterinarioNombre;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(name = "motivo", nullable = false, length = 255)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 15)
    private EstadoCita estado;
}
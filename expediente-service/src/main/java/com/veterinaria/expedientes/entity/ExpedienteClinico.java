package com.veterinaria.expedientes.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

@Entity
@Table(
        name = "expedientes",
        uniqueConstraints = @UniqueConstraint(name = "uq_expediente_cita", columnNames = "cita_id"),
        indexes = @Index(name = "idx_expediente_mascota", columnList = "mascota_id")
)
@Getter
@Setter
public class ExpedienteClinico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cita_id", nullable = false)
    private Long citaId;

    @Column(name = "mascota_id", nullable = false)
    private Long mascotaId;

    @Column(name = "diagnostico", nullable = false, length = 1000)
    private String diagnostico;

    @Column(name = "tratamiento", nullable = false, length = 1000)
    private String tratamiento;

    @Column(name = "peso_kg", nullable = false, precision = 10, scale = 2)
    @Check(constraints = "peso_kg > 0")
    private BigDecimal pesoKg;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @PrePersist
    void antesDePersistir() {
        fechaRegistro = LocalDateTime.now();
    }
}

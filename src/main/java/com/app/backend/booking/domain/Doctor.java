package com.app.backend.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "doctor")
public class Doctor {

    @Id
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    protected Doctor() {}

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}

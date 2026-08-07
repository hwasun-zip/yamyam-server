package com.yamyam.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Restaurant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String category;
    private Double latitude;
    private Double longitude;
    private String status;
    private String roadAddress;

    protected Restaurant() {}

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
}

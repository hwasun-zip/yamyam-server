package com.yamyam.domain;

import jakarta.persistence.*;

@Entity
public class Restaurant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String category;
    private Double latitude;
    private Double longitude;
    @Column(length = 50)  private String status;
    @Column(length = 300) private String roadAddress;

    protected Restaurant() {}
    public Restaurant(String name, String category, Double latitude, Double longitude,
                      String status, String roadAddress) {
        this.name = name; this.category = category;
        this.latitude = latitude; this.longitude = longitude;
        this.status = status; this.roadAddress = roadAddress;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getStatus() { return status; }
    public String getRoadAddress() { return roadAddress; }
}

package com.yamyam.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class FoodWeatherModel {
    @Id
    private String food;
    private Double coefTemp;
    private Double coefPrecip;
    private Double coefHumidity;
    private Double coefWind;
    private Double intercept;
    private Double meanPred;
    private Double stdPred;

    protected FoodWeatherModel() {}
}

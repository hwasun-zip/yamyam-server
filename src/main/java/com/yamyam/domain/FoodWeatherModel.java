package com.yamyam.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class FoodWeatherModel {
    @Id
    private String food;
    private Double coefTemp, coefPrecip, coefHumidity, coefWind, intercept, meanPred, stdPred;

    protected FoodWeatherModel() {}
    public FoodWeatherModel(String food, Double coefTemp, Double coefPrecip, Double coefHumidity,
                            Double coefWind, Double intercept, Double meanPred, Double stdPred) {
        this.food = food; this.coefTemp = coefTemp; this.coefPrecip = coefPrecip;
        this.coefHumidity = coefHumidity; this.coefWind = coefWind; this.intercept = intercept;
        this.meanPred = meanPred; this.stdPred = stdPred;
    }
    public String getFood() { return food; }
    public Double getCoefTemp() { return coefTemp; }
    public Double getCoefPrecip() { return coefPrecip; }
    public Double getCoefHumidity() { return coefHumidity; }
    public Double getCoefWind() { return coefWind; }
    public Double getIntercept() { return intercept; }
    public Double getMeanPred() { return meanPred; }
    public Double getStdPred() { return stdPred; }
}

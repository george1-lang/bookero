package com.bookero.airport;

import com.bookero.common.AssignedIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "airport")
public class AirportEntity extends AssignedIdEntity<String> {
    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    private String city;

    private String country;

    private Double lat;

    private Double lon;

    public AirportEntity() {
    }

    public AirportEntity(String code, String name, String city, String country, Double lat, Double lon) {
        this.code = code;
        this.name = name;
        this.city = city;
        this.country = country;
        this.lat = lat;
        this.lon = lon;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon;
    }
  @Override
  public String getId() {
    return getCode();
  }
}

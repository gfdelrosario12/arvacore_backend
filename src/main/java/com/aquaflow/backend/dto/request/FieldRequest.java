package com.aquaflow.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldRequest {

    @NotBlank(message = "Field name must not be blank")
    private String name;

    private String boundaryGeoJson;

    @Positive(message = "Area must be positive")
    private Double areaHectares;

    private Double lengthMeters;
    private Double widthMeters;
    private Double totalArea;
    private String areaUnit;
    private String shapeType;
    private String operationalStatus;
    private String description;
    private String locationAddress;
    private String ownerUsername;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBoundaryGeoJson() { return boundaryGeoJson; }
    public void setBoundaryGeoJson(String boundaryGeoJson) { this.boundaryGeoJson = boundaryGeoJson; }
    public Double getAreaHectares() { return areaHectares; }
    public void setAreaHectares(Double areaHectares) { this.areaHectares = areaHectares; }
}


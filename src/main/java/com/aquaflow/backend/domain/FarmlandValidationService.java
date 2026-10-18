package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.response.FarmlandValidationResponse;

public interface FarmlandValidationService {

    FarmlandValidationResponse validateFarmlandReadiness(Long fieldId);

    void updateFarmlandOperationalStatus(Long fieldId);
}


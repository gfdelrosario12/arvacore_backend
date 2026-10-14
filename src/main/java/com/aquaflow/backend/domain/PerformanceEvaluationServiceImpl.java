package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.ReferenceMeasurementCreateRequest;
import com.aquaflow.backend.dto.response.ReferenceMeasurementResponse;
import com.aquaflow.backend.dto.response.SystemEvaluationSummaryResponse;
import com.aquaflow.backend.entity.CommandState;
import com.aquaflow.backend.entity.CommandStateTransitionHistory;
import com.aquaflow.backend.entity.EvaluationReferenceMeasurement;
import com.aquaflow.backend.entity.IrrigationCommandRecord;
import com.aquaflow.backend.infrastructure.exception.ValidationException;
import com.aquaflow.backend.persistence.CommandStateTransitionHistoryRepository;
import com.aquaflow.backend.persistence.EvaluationReferenceMeasurementRepository;
import com.aquaflow.backend.persistence.IrrigationCommandRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceEvaluationServiceImpl implements PerformanceEvaluationService {

    private final EvaluationReferenceMeasurementRepository referenceRepository;
    private final IrrigationCommandRecordRepository commandRepository;
    private final CommandStateTransitionHistoryRepository transitionHistoryRepository;

    @Override
    @Transactional(readOnly = true)
    public SystemEvaluationSummaryResponse getSystemEvaluationSummary(String nodeId, Long fieldId) {
        log.info("Generating system evaluation summary for nodeId={}, fieldId={}", nodeId, fieldId);

        return SystemEvaluationSummaryResponse.builder()
                .waterLevel(buildWaterLevelEvaluation(nodeId))
                .soilMoisture(buildSoilMoistureEvaluation(nodeId))
                .activation(buildActivationEvaluation(fieldId))
                .deactivation(buildDeactivationEvaluation(fieldId))
                .responseTime(buildResponseTimeEvaluation(fieldId))
                .waterConsumption(buildWaterConsumptionEvaluation())
                .build();
    }

    @Override
    @Transactional
    public ReferenceMeasurementResponse recordReferenceMeasurement(ReferenceMeasurementCreateRequest request, String actor) {
        if (!"WATER_LEVEL".equalsIgnoreCase(request.getIndicatorType()) &&
            !"SOIL_MOISTURE".equalsIgnoreCase(request.getIndicatorType())) {
            throw new ValidationException("Indicator type must be either 'WATER_LEVEL' or 'SOIL_MOISTURE'");
        }

        double reported = request.getReportedValue();
        double ref = request.getReferenceValue();
        double absError = Math.abs(reported - ref);

        Double pctError = null;
        Double accuracyPct = null;

        if (Math.abs(ref) > 0.0001) {
            pctError = (absError / Math.abs(ref)) * 100.0;
            accuracyPct = Math.max(0.0, Math.min(100.0, 100.0 - pctError));
        }

        EvaluationReferenceMeasurement entity = EvaluationReferenceMeasurement.builder()
                .indicatorType(request.getIndicatorType().toUpperCase().trim())
                .nodeId(request.getNodeId().trim())
                .monitoringZoneId(request.getMonitoringZoneId())
                .reportedValue(reported)
                .referenceValue(ref)
                .absoluteError(absError)
                .percentageError(pctError)
                .accuracyPercentage(accuracyPct)
                .measurementUnit(request.getMeasurementUnit())
                .instrumentDetails(request.getInstrumentDetails())
                .notes(request.getNotes())
                .measuredBy(actor != null && !actor.isBlank() ? actor : "SYSTEM")
                .measuredAt(request.getMeasuredAt() != null ? request.getMeasuredAt() : LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        EvaluationReferenceMeasurement saved = referenceRepository.save(entity);
        log.info("Saved evaluation reference measurement id={} for indicator={}", saved.getId(), saved.getIndicatorType());
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReferenceMeasurementResponse> getReferenceMeasurements(String indicatorType, String nodeId) {
        List<EvaluationReferenceMeasurement> list;
        if (nodeId != null && !nodeId.isBlank()) {
            list = referenceRepository.findByIndicatorTypeAndNodeIdOrderByMeasuredAtDesc(indicatorType.toUpperCase().trim(), nodeId.trim());
        } else {
            list = referenceRepository.findByIndicatorTypeOrderByMeasuredAtDesc(indicatorType.toUpperCase().trim());
        }
        return list.stream().map(this::toResponse).toList();
    }

    private SystemEvaluationSummaryResponse.WaterLevelEvaluation buildWaterLevelEvaluation(String nodeId) {
        List<EvaluationReferenceMeasurement> refs = (nodeId != null && !nodeId.isBlank())
                ? referenceRepository.findByIndicatorTypeAndNodeIdOrderByMeasuredAtDesc("WATER_LEVEL", nodeId.trim())
                : referenceRepository.findByIndicatorTypeOrderByMeasuredAtDesc("WATER_LEVEL");

        if (refs.isEmpty()) {
            return SystemEvaluationSummaryResponse.WaterLevelEvaluation.builder()
                    .capabilityStatus("PARTIALLY_MEASURABLE")
                    .statusDescription("Reported sensor water level available via ultrasonic sensor (h = 20.0cm - d). Awaiting manual ground-truth reference measurements to calculate accuracy percentage.")
                    .sampleCount(0)
                    .calculationFormula("Absolute Error = |Reported - Reference|; Accuracy (%) = 100 - (Absolute Error / |Reference|) * 100 (when Reference != 0)")
                    .recentComparisons(Collections.emptyList())
                    .build();
        }

        double sumAbsError = 0.0;
        double sumAccuracy = 0.0;
        int validAccuracyCount = 0;

        for (EvaluationReferenceMeasurement m : refs) {
            sumAbsError += m.getAbsoluteError();
            if (m.getAccuracyPercentage() != null) {
                sumAccuracy += m.getAccuracyPercentage();
                validAccuracyCount++;
            }
        }

        double meanAbsError = sumAbsError / refs.size();
        Double meanAccuracy = validAccuracyCount > 0 ? (sumAccuracy / validAccuracyCount) : null;
        EvaluationReferenceMeasurement latest = refs.get(0);

        return SystemEvaluationSummaryResponse.WaterLevelEvaluation.builder()
                .capabilityStatus("MEASURABLE")
                .statusDescription("Water level accuracy evaluated against independent physical gauge / ruler ground-truth measurements.")
                .latestReportedCm(latest.getReportedValue())
                .latestReferenceCm(latest.getReferenceValue())
                .meanAbsoluteErrorCm(meanAbsError)
                .meanAccuracyPercentage(meanAccuracy)
                .sampleCount(refs.size())
                .calculationFormula("Absolute Error = |Reported - Reference|; Accuracy (%) = 100 - (Absolute Error / |Reference|) * 100 (clamped 0-100%)")
                .recentComparisons(refs.stream().limit(10).map(this::toResponse).toList())
                .build();
    }

    private SystemEvaluationSummaryResponse.SoilMoistureEvaluation buildSoilMoistureEvaluation(String nodeId) {
        List<EvaluationReferenceMeasurement> refs = (nodeId != null && !nodeId.isBlank())
                ? referenceRepository.findByIndicatorTypeAndNodeIdOrderByMeasuredAtDesc("SOIL_MOISTURE", nodeId.trim())
                : referenceRepository.findByIndicatorTypeOrderByMeasuredAtDesc("SOIL_MOISTURE");

        if (refs.isEmpty()) {
            return SystemEvaluationSummaryResponse.SoilMoistureEvaluation.builder()
                    .capabilityStatus("NOT_MEASURABLE")
                    .statusDescription("Not Measurable — Calibrated Reference Measurement Required. Capacitive sensor uses two-point dry/wet ADC mapping (3200 air / 1500 water). Independent TDR/gravimetric soil test reference required to establish verified accuracy.")
                    .sampleCount(0)
                    .calibrationNotes("Standard Capacitive v1.2 analog calibration: AIR_ADC=3200 (0%), WATER_ADC=1500 (100%).")
                    .calculationFormula("Absolute Error = |Reported % - Calibrated Reference %|")
                    .recentComparisons(Collections.emptyList())
                    .build();
        }

        double sumAbsError = 0.0;
        double sumAccuracy = 0.0;
        int validAccuracyCount = 0;

        for (EvaluationReferenceMeasurement m : refs) {
            sumAbsError += m.getAbsoluteError();
            if (m.getAccuracyPercentage() != null) {
                sumAccuracy += m.getAccuracyPercentage();
                validAccuracyCount++;
            }
        }

        double meanAbsError = sumAbsError / refs.size();
        Double meanAccuracy = validAccuracyCount > 0 ? (sumAccuracy / validAccuracyCount) : null;
        EvaluationReferenceMeasurement latest = refs.get(0);

        return SystemEvaluationSummaryResponse.SoilMoistureEvaluation.builder()
                .capabilityStatus("MEASURABLE")
                .statusDescription("Soil moisture accuracy validated against calibrated soil instrument reference measurements.")
                .latestReportedPercentage(latest.getReportedValue())
                .latestReferencePercentage(latest.getReferenceValue())
                .meanAbsoluteError(meanAbsError)
                .meanAccuracyPercentage(meanAccuracy)
                .sampleCount(refs.size())
                .calibrationNotes("Sensor mapping: AIR_ADC=3200, WATER_ADC=1500.")
                .calculationFormula("Absolute Error = |Reported - Reference|; Accuracy (%) = 100 - (Absolute Error / |Reference|) * 100")
                .recentComparisons(refs.stream().limit(10).map(this::toResponse).toList())
                .build();
    }

    private SystemEvaluationSummaryResponse.ActivationEvaluation buildActivationEvaluation(Long fieldId) {
        List<IrrigationCommandRecord> records = (fieldId != null)
                ? commandRepository.findByTargetFieldIdOrderByCreatedAtDesc(fieldId)
                : commandRepository.findAll();

        int totalAttempts = 0;
        int confirmedSuccesses = 0;
        int failedAttempts = 0;

        for (IrrigationCommandRecord r : records) {
            if ("START".equalsIgnoreCase(r.getCommandType()) || "OPEN_VALVE".equalsIgnoreCase(r.getCommandType())) {
                totalAttempts++;
                if (r.getCurrentState() == CommandState.EDGE_ACKNOWLEDGED ||
                    r.getCurrentState() == CommandState.EXECUTING ||
                    r.getCurrentState() == CommandState.COMPLETED) {
                    confirmedSuccesses++;
                } else if (r.getCurrentState() == CommandState.FAILED || r.getCurrentState() == CommandState.CANCELLED) {
                    failedAttempts++;
                }
            }
        }

        Double rate = totalAttempts > 0 ? ((double) confirmedSuccesses / (double) totalAttempts) * 100.0 : null;

        return SystemEvaluationSummaryResponse.ActivationEvaluation.builder()
                .capabilityStatus("PARTIALLY_MEASURABLE")
                .statusDescription("Evaluated via edge node downlink acknowledgment and state machine confirmation (ACCEPTED -> QUEUED -> TRANSMITTED -> EDGE_ACKNOWLEDGED). Note: Physical pump contactor confirmation is inferred from edge radio ACK; flow sensor confirmation is not equipped.")
                .totalAttempts(totalAttempts)
                .confirmedSuccesses(confirmedSuccesses)
                .failedAttempts(failedAttempts)
                .successRatePercentage(rate)
                .confirmationSource("Edge Node Downlink LoRaWAN/Wi-Fi ACK & Command State Machine Record")
                .calculationFormula("Activation Success Rate (%) = (Confirmed Acknowledged Activations / Total Activation Commands) * 100")
                .build();
    }

    private SystemEvaluationSummaryResponse.DeactivationEvaluation buildDeactivationEvaluation(Long fieldId) {
        List<IrrigationCommandRecord> records = (fieldId != null)
                ? commandRepository.findByTargetFieldIdOrderByCreatedAtDesc(fieldId)
                : commandRepository.findAll();

        int totalAttempts = 0;
        int confirmedSuccesses = 0;
        int failedAttempts = 0;

        for (IrrigationCommandRecord r : records) {
            if ("STOP".equalsIgnoreCase(r.getCommandType()) ||
                "CLOSE_VALVE".equalsIgnoreCase(r.getCommandType()) ||
                "EMERGENCY_STOP".equalsIgnoreCase(r.getCommandType())) {
                totalAttempts++;
                if (r.getCurrentState() == CommandState.EDGE_ACKNOWLEDGED ||
                    r.getCurrentState() == CommandState.COMPLETED) {
                    confirmedSuccesses++;
                } else if (r.getCurrentState() == CommandState.FAILED || r.getCurrentState() == CommandState.CANCELLED) {
                    failedAttempts++;
                }
            }
        }

        Double rate = totalAttempts > 0 ? ((double) confirmedSuccesses / (double) totalAttempts) * 100.0 : null;

        return SystemEvaluationSummaryResponse.DeactivationEvaluation.builder()
                .capabilityStatus("PARTIALLY_MEASURABLE")
                .statusDescription("Evaluated via edge node deactivation / emergency stop downlink acknowledgment. Note: Physical valve shutoff confirmed via microcontroller relay control state; inline hydraulic zero-flow feedback is not installed.")
                .totalAttempts(totalAttempts)
                .confirmedSuccesses(confirmedSuccesses)
                .failedAttempts(failedAttempts)
                .successRatePercentage(rate)
                .confirmationSource("Edge Node Radio ACK & Emergency Stop State Machine")
                .calculationFormula("Deactivation Success Rate (%) = (Confirmed Acknowledged Deactivations / Total Deactivation Commands) * 100")
                .build();
    }

    private SystemEvaluationSummaryResponse.ResponseTimeEvaluation buildResponseTimeEvaluation(Long fieldId) {
        List<IrrigationCommandRecord> records = (fieldId != null)
                ? commandRepository.findByTargetFieldIdOrderByCreatedAtDesc(fieldId)
                : commandRepository.findAll();

        List<Double> latenciesMs = new ArrayList<>();
        int timeouts = 0;

        for (IrrigationCommandRecord r : records) {
            List<CommandStateTransitionHistory> transitions =
                    transitionHistoryRepository.findByCorrelationIdOrderByTimestampAsc(r.getCorrelationId());

            LocalDateTime issued = null;
            LocalDateTime acked = null;

            for (CommandStateTransitionHistory h : transitions) {
                if (h.getToState() == CommandState.ACCEPTED && issued == null) {
                    issued = h.getTimestamp();
                }
                if (h.getToState() == CommandState.EDGE_ACKNOWLEDGED && acked == null) {
                    acked = h.getTimestamp();
                }
            }

            if (issued == null && r.getCreatedAt() != null) {
                issued = r.getCreatedAt();
            }

            if (issued != null && acked != null) {
                long ms = Duration.between(issued, acked).toMillis();
                if (ms >= 0) {
                    latenciesMs.add((double) ms);
                }
            } else if (r.getCurrentState() == CommandState.FAILED &&
                       r.getFailureReason() != null &&
                       r.getFailureReason().toLowerCase().contains("timeout")) {
                timeouts++;
            }
        }

        if (latenciesMs.isEmpty()) {
            return SystemEvaluationSummaryResponse.ResponseTimeEvaluation.builder()
                    .capabilityStatus("PARTIALLY_MEASURABLE")
                    .statusDescription("Command-to-Acknowledgment latency tracking is active. No completed command round-trips recorded yet in this evaluation period.")
                    .measuredSampleCount(0)
                    .timeoutCount(timeouts)
                    .clockSyncNotes("Measured across server-synchronized NTP timestamps; edge acknowledgment uses correlation ID timestamps.")
                    .calculationFormula("Response Time = Timestamp(EDGE_ACKNOWLEDGED) - Timestamp(COMMAND_ACCEPTED)")
                    .build();
        }

        latenciesMs.sort(Double::compareTo);
        double min = latenciesMs.get(0);
        double max = latenciesMs.get(latenciesMs.size() - 1);
        double sum = latenciesMs.stream().mapToDouble(Double::doubleValue).sum();
        double avg = sum / latenciesMs.size();

        int p95Index = (int) Math.ceil(latenciesMs.size() * 0.95) - 1;
        p95Index = Math.max(0, Math.min(p95Index, latenciesMs.size() - 1));
        double p95 = latenciesMs.get(p95Index);

        return SystemEvaluationSummaryResponse.ResponseTimeEvaluation.builder()
                .capabilityStatus("MEASURABLE")
                .statusDescription("Command-to-Acknowledgment response latency accurately measured between backend command acceptance and edge node radio ACK.")
                .measuredSampleCount(latenciesMs.size())
                .avgCommandToAckMs(avg)
                .minCommandToAckMs(min)
                .maxCommandToAckMs(max)
                .p95CommandToAckMs(p95)
                .timeoutCount(timeouts)
                .clockSyncNotes("Server timestamp comparison with edge acknowledgment confirmation. Clock drift between gateway and server is constrained by NTP.")
                .calculationFormula("Latency = T_ack - T_issued (milliseconds)")
                .build();
    }

    private SystemEvaluationSummaryResponse.WaterConsumptionEvaluation buildWaterConsumptionEvaluation() {
        return SystemEvaluationSummaryResponse.WaterConsumptionEvaluation.builder()
                .capabilityStatus("NOT_MEASURABLE")
                .statusDescription("Not Measurable — Flow Meter or Calibrated Consumption Model Required.")
                .measurementType("UNAVAILABLE")
                .currentFlowRateLpm(null)
                .cumulativeConsumptionLiters(null)
                .hardwareRequirement("Requires an in-line pulse water meter (e.g., YF-B5 / YF-S201 Hall-effect flow sensor) or a calibrated pump flow rate curve (L/min) correlated with verified runtime.")
                .missingComponentExplanation("Current ArvaNode hardware carries only a capacitive soil moisture sensor (GPIO 34) and ultrasonic water depth sensor (GPIO 5/18). Physical volumetric consumption cannot be claimed without a verified flow measurement source.")
                .build();
    }

    private ReferenceMeasurementResponse toResponse(EvaluationReferenceMeasurement entity) {
        return ReferenceMeasurementResponse.builder()
                .id(entity.getId())
                .indicatorType(entity.getIndicatorType())
                .nodeId(entity.getNodeId())
                .monitoringZoneId(entity.getMonitoringZoneId())
                .reportedValue(entity.getReportedValue())
                .referenceValue(entity.getReferenceValue())
                .absoluteError(entity.getAbsoluteError())
                .percentageError(entity.getPercentageError())
                .accuracyPercentage(entity.getAccuracyPercentage())
                .measurementUnit(entity.getMeasurementUnit())
                .instrumentDetails(entity.getInstrumentDetails())
                .notes(entity.getNotes())
                .measuredBy(entity.getMeasuredBy())
                .measuredAt(entity.getMeasuredAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

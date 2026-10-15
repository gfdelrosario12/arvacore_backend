package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.AutoIrrigationConfigRequest;
import com.aquaflow.backend.dto.request.AwdThresholdConfigRequest;
import com.aquaflow.backend.dto.response.AutoIrrigationConfigResponse;
import com.aquaflow.backend.entity.AutoIrrigationConfig;
import com.aquaflow.backend.entity.AwdThresholdConfig;
import com.aquaflow.backend.entity.CropGrowthStage;
import com.aquaflow.backend.entity.Field;
import com.aquaflow.backend.entity.ScheduleMode;
import com.aquaflow.backend.infrastructure.event.SystemEvent;
import com.aquaflow.backend.infrastructure.event.SystemEventPublisher;
import com.aquaflow.backend.infrastructure.event.SystemEventType;
import com.aquaflow.backend.infrastructure.exception.ResourceNotFoundException;
import com.aquaflow.backend.infrastructure.util.DtoMapper;
import com.aquaflow.backend.persistence.AutoIrrigationConfigRepository;
import com.aquaflow.backend.persistence.FieldRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class AwdConfigServiceImpl implements AwdConfigService {

    private static final Logger log = LoggerFactory.getLogger(AwdConfigServiceImpl.class);

    private final FieldRepository fieldRepository;
    private final AutoIrrigationConfigRepository autoIrrigationConfigRepository;
    private final AwdConfigValidator awdConfigValidator;
    private final SystemEventPublisher systemEventPublisher;
    private final EdgeNodeSyncService edgeNodeSyncService;

    @Autowired
    public AwdConfigServiceImpl(FieldRepository fieldRepository,
                                AutoIrrigationConfigRepository autoIrrigationConfigRepository,
                                AwdConfigValidator awdConfigValidator,
                                SystemEventPublisher systemEventPublisher,
                                @Autowired(required = false) EdgeNodeSyncService edgeNodeSyncService) {
        this.fieldRepository = fieldRepository;
        this.autoIrrigationConfigRepository = autoIrrigationConfigRepository;
        this.awdConfigValidator = awdConfigValidator;
        this.systemEventPublisher = systemEventPublisher;
        this.edgeNodeSyncService = edgeNodeSyncService;
    }

    public AwdConfigServiceImpl(FieldRepository fieldRepository,
                                AutoIrrigationConfigRepository autoIrrigationConfigRepository,
                                AwdConfigValidator awdConfigValidator,
                                SystemEventPublisher systemEventPublisher) {
        this(fieldRepository, autoIrrigationConfigRepository, awdConfigValidator, systemEventPublisher, null);
    }

    @Override
    @Transactional(readOnly = true)
    public AutoIrrigationConfigResponse getAwdConfig(Long fieldId) {
        log.info("Fetching AWD config for fieldId: {}", fieldId);

        Field field = fieldRepository.findById(fieldId)
                .orElseThrow(() -> new ResourceNotFoundException("Field not found with id: " + fieldId));

        AutoIrrigationConfig config = autoIrrigationConfigRepository.findByFieldId(fieldId)
                .orElseGet(() -> createDefaultConfig(field));

        return DtoMapper.toAutoIrrigationConfigResponse(config);
    }

    @Override
    public AutoIrrigationConfigResponse updateAwdConfig(Long fieldId, AutoIrrigationConfigRequest request) {
        log.info("Updating AWD config for fieldId: {}", fieldId);

        Field field = fieldRepository.findById(fieldId)
                .orElseThrow(() -> new ResourceNotFoundException("Field not found with id: " + fieldId));

        awdConfigValidator.validate(request);

        AutoIrrigationConfig config = autoIrrigationConfigRepository.findByFieldId(fieldId)
                .orElseGet(() -> {
                    AutoIrrigationConfig newConfig = new AutoIrrigationConfig();
                    newConfig.setField(field);
                    newConfig.setConfigVersion(0L);
                    return newConfig;
                });

        Long nextVersion = (config.getConfigVersion() != null) ? config.getConfigVersion() + 1L : 1L;
        config.setConfigVersion(nextVersion);

        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled());
        }
        if (request.getMaxDurationMinutes() != null) {
            config.setMaxDurationMinutes(request.getMaxDurationMinutes());
        }
        if (request.getMinCooldownMinutes() != null) {
            config.setMinCooldownMinutes(request.getMinCooldownMinutes());
        }
        if (request.getAllowedStartHour() != null) {
            config.setAllowedStartHour(request.getAllowedStartHour());
        }
        if (request.getAllowedEndHour() != null) {
            config.setAllowedEndHour(request.getAllowedEndHour());
        }
        if (request.getTargetFloodDepthCm() != null) {
            config.setTargetFloodDepthCm(request.getTargetFloodDepthCm());
        }
        if (request.getRainDelayHours() != null) {
            config.setRainDelayHours(request.getRainDelayHours());
        }
        if (request.getMinConfidenceThreshold() != null) {
            config.setMinConfidenceThreshold(request.getMinConfidenceThreshold());
        }
        if (request.getUpdatedBy() != null) {
            config.setUpdatedBy(request.getUpdatedBy());
        }
        if (request.getChangeReason() != null) {
            config.setChangeReason(request.getChangeReason());
        }

        if (request.getThresholds() != null && !request.getThresholds().isEmpty()) {
            config.getThresholds().clear();
            for (AwdThresholdConfigRequest req : request.getThresholds()) {
                AwdThresholdConfig threshold = AwdThresholdConfig.builder()
                        .autoIrrigationConfig(config)
                        .growthStage(req.getGrowthStage())
                        .triggerMoisturePercentage(req.getTriggerMoisturePercentage())
                        .targetMoisturePercentage(req.getTargetMoisturePercentage())
                        .targetFloodDepthCm(req.getTargetFloodDepthCm())
                        .build();
                config.getThresholds().add(threshold);
            }
        }

        AutoIrrigationConfig savedConfig = autoIrrigationConfigRepository.save(config);
        AutoIrrigationConfigResponse response = DtoMapper.toAutoIrrigationConfigResponse(savedConfig);

        if (systemEventPublisher != null) {
            try {
                systemEventPublisher.publish(SystemEvent.builder()
                        .eventType(SystemEventType.CONFIG_SYNCED)
                        .fieldId(fieldId)
                        .aggregateId(fieldId.toString())
                        .payload(response)
                        .build());
            } catch (Exception e) {
                log.warn("Failed to publish CONFIG_SYNCED event for fieldId {}: {}", fieldId, e.getMessage());
            }
        }

        if (edgeNodeSyncService != null) {
            try {
                edgeNodeSyncService.syncConfigForField(fieldId);
            } catch (Exception e) {
                log.warn("Failed to trigger edge node config sync for fieldId {}: {}", fieldId, e.getMessage());
            }
        }

        return response;
    }

    private AutoIrrigationConfig createDefaultConfig(Field field) {
        AutoIrrigationConfig config = AutoIrrigationConfig.builder()
                .field(field)
                .enabled(true)
                .maxDurationMinutes(120)
                .minCooldownMinutes(360)
                .allowedStartHour(6)
                .allowedEndHour(18)
                .targetFloodDepthCm(5.0)
                .rainDelayHours(24)
                .minConfidenceThreshold(0.80)
                .scheduleMode(ScheduleMode.AUTOMATIC)
                .configVersion(1L)
                .updatedBy("SYSTEM")
                .changeReason("Default initial AWD configuration")
                .thresholds(new ArrayList<>())
                .build();

        List<AwdThresholdConfig> defaultThresholds = List.of(
                AwdThresholdConfig.builder().autoIrrigationConfig(config).growthStage(CropGrowthStage.VEGETATIVE).triggerMoisturePercentage(40.0).targetMoisturePercentage(80.0).targetFloodDepthCm(5.0).build(),
                AwdThresholdConfig.builder().autoIrrigationConfig(config).growthStage(CropGrowthStage.REPRODUCTIVE).triggerMoisturePercentage(60.0).targetMoisturePercentage(90.0).targetFloodDepthCm(5.0).build(),
                AwdThresholdConfig.builder().autoIrrigationConfig(config).growthStage(CropGrowthStage.RIPENING).triggerMoisturePercentage(30.0).targetMoisturePercentage(70.0).targetFloodDepthCm(3.0).build(),
                AwdThresholdConfig.builder().autoIrrigationConfig(config).growthStage(CropGrowthStage.FALLOW).triggerMoisturePercentage(20.0).targetMoisturePercentage(50.0).targetFloodDepthCm(0.0).build()
        );
        config.getThresholds().addAll(defaultThresholds);

        return autoIrrigationConfigRepository.save(config);
    }
}


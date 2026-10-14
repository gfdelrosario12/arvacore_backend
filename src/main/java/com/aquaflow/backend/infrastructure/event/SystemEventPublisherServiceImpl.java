package com.aquaflow.backend.infrastructure.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class SystemEventPublisherServiceImpl implements SystemEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SystemEventPublisherServiceImpl.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    public SystemEventPublisherServiceImpl(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(SystemEvent event) {
        if (event == null) {
            return;
        }
        log.debug("Publishing internal system event: {} for aggregate: {}", event.getEventType(), event.getAggregateId());
        applicationEventPublisher.publishEvent(event);
    }

    @Override
    public void publishTelemetryReceived(String nodeId, Long fieldId, int readingsIngested) {
        SystemEvent event = SystemEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(SystemEventType.TELEMETRY_RECEIVED)
                .timestamp(LocalDateTime.now())
                .aggregateId(nodeId)
                .fieldId(fieldId)
                .payload(Map.of(
                        "nodeId", nodeId != null ? nodeId : "",
                        "readingsIngested", readingsIngested
                ))
                .build();
        publish(event);
    }

    @Override
    public void publishNodeStatusChanged(String nodeId, Long fieldId, String oldState, String newState) {
        SystemEvent event = SystemEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(SystemEventType.NODE_STATUS_CHANGED)
                .timestamp(LocalDateTime.now())
                .aggregateId(nodeId)
                .fieldId(fieldId)
                .payload(Map.of(
                        "nodeId", nodeId != null ? nodeId : "",
                        "oldState", oldState != null ? oldState : "",
                        "newState", newState != null ? newState : ""
                ))
                .build();
        publish(event);
    }

    @Override
    public void publishAlarm(String aggregateId, Long fieldId, String alarmType, String severity, String message) {
        SystemEvent event = SystemEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(SystemEventType.ALARM_TRIGGERED)
                .timestamp(LocalDateTime.now())
                .aggregateId(aggregateId)
                .fieldId(fieldId)
                .payload(Map.of(
                        "alarmType", alarmType != null ? alarmType : "",
                        "severity", severity != null ? severity : "",
                        "message", message != null ? message : ""
                ))
                .build();
        publish(event);
    }

    @Override
    public void publishEmergencyStop(String aggregateId, Long fieldId, String reason) {
        SystemEvent event = SystemEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(SystemEventType.EMERGENCY_STOP_ACTIVATED)
                .timestamp(LocalDateTime.now())
                .aggregateId(aggregateId)
                .fieldId(fieldId)
                .payload(Map.of(
                        "reason", reason != null ? reason : "Emergency stop requested"
                ))
                .build();
        publish(event);
    }

    @Override
    public void publishMasterStateChanged(boolean masterEnabled, String updatedBy) {
        SystemEvent event = SystemEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(SystemEventType.MASTER_STATE_CHANGED)
                .timestamp(LocalDateTime.now())
                .aggregateId("SYSTEM")
                .payload(Map.of(
                        "masterEnabled", masterEnabled,
                        "updatedBy", updatedBy != null ? updatedBy : "SYSTEM"
                ))
                .build();
        publish(event);
    }

    @Override
    public void publishPairingRequestUpdated(String nodeId, String status) {
        SystemEvent event = SystemEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(SystemEventType.PAIRING_REQUEST_UPDATED)
                .timestamp(LocalDateTime.now())
                .aggregateId(nodeId)
                .payload(Map.of(
                        "nodeId", nodeId != null ? nodeId : "",
                        "status", status != null ? status : ""
                ))
                .build();
        publish(event);
    }
}


package com.aquaflow.backend.infrastructure.event;

public interface SystemEventPublisher {

    void publish(SystemEvent event);

    void publishTelemetryReceived(String nodeId, Long fieldId, int readingsIngested);

    void publishNodeStatusChanged(String nodeId, Long fieldId, String oldState, String newState);

    void publishAlarm(String aggregateId, Long fieldId, String alarmType, String severity, String message);

    void publishEmergencyStop(String aggregateId, Long fieldId, String reason);

    void publishMasterStateChanged(boolean masterEnabled, String updatedBy);

    void publishPairingRequestUpdated(String nodeId, String status);
}


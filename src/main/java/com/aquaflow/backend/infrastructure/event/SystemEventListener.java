package com.aquaflow.backend.infrastructure.event;

import com.aquaflow.backend.domain.AlertService;
import com.aquaflow.backend.domain.AuditLogService;
import com.aquaflow.backend.dto.response.AutoIrrigationConfigResponse;
import com.aquaflow.backend.dto.response.CommandStatusResponse;
import com.aquaflow.backend.dto.response.IrrigationDecisionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SystemEventListener {

    private static final Logger log = LoggerFactory.getLogger(SystemEventListener.class);

    private final AuditLogService auditLogService;
    private final AlertService alertService;

    @Autowired
    public SystemEventListener(
            @Autowired(required = false) AuditLogService auditLogService,
            @Autowired(required = false) AlertService alertService) {
        this.auditLogService = auditLogService;
        this.alertService = alertService;
    }

    public SystemEventListener(AuditLogService auditLogService) {
        this(auditLogService, null);
    }

    @EventListener
    public void handleSystemEvent(SystemEvent event) {
        if (event == null || event.getEventType() == null) {
            return;
        }
        log.debug("Processing system event: {} ({})", event.getEventType(), event.getEventId());

        if (auditLogService != null) {
            try {
                if (event.getEventType() == SystemEventType.NODE_STATUS_CHANGED
                        || event.getEventType() == SystemEventType.ALARM_TRIGGERED
                        || event.getEventType() == SystemEventType.CONFIG_SYNCED
                        || event.getEventType() == SystemEventType.PAIRING_REQUEST_UPDATED
                        || event.getEventType() == SystemEventType.MASTER_STATE_CHANGED) {
                    auditLogService.logSystemEvent(
                            event.getEventType().name(),
                            "SYSTEM",
                            "NODE",
                            event.getAggregateId() != null ? event.getAggregateId() : "SYSTEM",
                            event.getAggregateId(),
                            null,
                            event.getEventType().name(),
                            event.getPayload()
                    );
                } else if (event.getEventType() == SystemEventType.EMERGENCY_STOP_ACTIVATED
                        || event.getEventType() == SystemEventType.IRRIGATION_DECISION_MADE
                        || event.getEventType() == SystemEventType.IRRIGATION_EXECUTION_UPDATED) {
                    auditLogService.logIrrigationEvent(
                            event.getEventType().name(),
                            "SYSTEM",
                            "IRRIGATION",
                            event.getFieldId() != null ? event.getFieldId().toString() : "FIELD",
                            event.getAggregateId(),
                            event.getPayload()
                    );
                }
            } catch (Exception e) {
                log.warn("Failed to record audit entry for event {}: {}", event.getEventId(), e.getMessage());
            }
        }

        if (alertService != null && event.getEventType() != SystemEventType.TELEMETRY_RECEIVED) {
            try {
                persistNotificationForSystemEvent(event);
            } catch (Exception e) {
                log.warn("Failed to record notification for event {}: {}", event.getEventId(), e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void persistNotificationForSystemEvent(SystemEvent event) {
        SystemEventType type = event.getEventType();
        Object payload = event.getPayload();
        Long fieldId = event.getFieldId();
        String aggId = event.getAggregateId();

        switch (type) {
            case PAIRING_REQUEST_UPDATED -> {
                String status = "";
                String nodeId = aggId;
                if (payload instanceof Map<?, ?> map) {
                    if (map.get("status") != null) status = map.get("status").toString();
                    if (map.get("nodeId") != null) nodeId = map.get("nodeId").toString();
                }
                String title = switch (status.toUpperCase()) {
                    case "PENDING" -> "Node Pairing Requested: " + nodeId;
                    case "APPROVED" -> "Node Pairing Approved: " + nodeId;
                    case "PAIRED" -> "Node Pairing Completed: " + nodeId;
                    case "REJECTED" -> "Node Pairing Rejected: " + nodeId;
                    case "REVOKED" -> "Node Unpaired: " + nodeId;
                    default -> "Node Pairing Updated: " + nodeId;
                };
                String severity = ("REJECTED".equalsIgnoreCase(status) || "REVOKED".equalsIgnoreCase(status))
                        ? "WARNING" : "INFO";
                String msg = String.format("Node %s pairing status transitioned to %s.", nodeId, status);
                alertService.createNotification(
                        "NODE_PAIRING_" + (status.isBlank() ? "UPDATED" : status.toUpperCase()),
                        title,
                        msg,
                        severity,
                        "hardware",
                        nodeId,
                        fieldId,
                        null,
                        null,
                        "SYSTEM",
                        "Node " + nodeId,
                        nodeId,
                        "Review node pairing and zone assignment in Field Dashboard.",
                        "PAIRING:" + nodeId + ":" + status.toUpperCase(),
                        2,
                        null
                );
            }
            case NODE_STATUS_CHANGED -> {
                String oldState = "";
                String newState = "";
                String nodeId = aggId;
                if (payload instanceof Map<?, ?> map) {
                    if (map.get("oldState") != null) oldState = map.get("oldState").toString();
                    if (map.get("newState") != null) newState = map.get("newState").toString();
                    if (map.get("nodeId") != null) nodeId = map.get("nodeId").toString();
                }
                if (oldState.equals(newState) && !newState.isEmpty()) {
                    return;
                }
                String title;
                String msg;
                String category = "hardware";
                String severity = "INFO";
                if (newState.startsWith("ZONE_")) {
                    String zId = newState.replace("ZONE_", "");
                    if (oldState.startsWith("ZONE_")) {
                        title = "Node Reassigned: " + nodeId;
                        msg = String.format("Node %s moved from Monitoring Zone %s to Monitoring Zone %s.",
                                nodeId, oldState.replace("ZONE_", "Q"), "Q" + zId);
                    } else {
                        title = "Node Assigned to Zone: " + nodeId;
                        msg = String.format("Node %s assigned to Monitoring Zone Q%s.", nodeId, zId);
                    }
                } else if ("UNASSIGNED".equalsIgnoreCase(newState)) {
                    title = "Node Unassigned: " + nodeId;
                    msg = String.format("Node %s was unassigned from its monitoring zone (%s).", nodeId, oldState);
                    severity = "WARNING";
                } else if ("OFFLINE".equalsIgnoreCase(newState)) {
                    title = "Node Offline: " + nodeId;
                    msg = String.format("Node %s lost connectivity (previous state: %s).", nodeId, oldState);
                    category = "connectivity";
                    severity = "CRITICAL";
                } else if ("HEALTHY".equalsIgnoreCase(newState) && "OFFLINE".equalsIgnoreCase(oldState)) {
                    title = "Node Back Online: " + nodeId;
                    msg = String.format("Node %s restored connectivity and is reporting normally.", nodeId);
                    category = "connectivity";
                    severity = "INFO";
                } else {
                    title = "Node Status Changed: " + nodeId;
                    msg = String.format("Node %s transitioned from %s to %s.", nodeId, oldState, newState);
                    if ("CRITICAL".equalsIgnoreCase(newState) || "DEGRADED".equalsIgnoreCase(newState)) {
                        severity = "WARNING";
                    }
                }
                alertService.createNotification(
                        "NODE_STATUS_CHANGED",
                        title,
                        msg,
                        severity,
                        category,
                        nodeId,
                        fieldId,
                        null,
                        null,
                        "SYSTEM",
                        "Node " + nodeId,
                        nodeId,
                        null,
                        "NODE_STATE:" + nodeId + ":" + oldState + "->" + newState,
                        2,
                        null
                );
            }
            case ALARM_TRIGGERED -> {
                String alarmType = "ALARM_TRIGGERED";
                String severity = "WARNING";
                String message = "System alarm triggered.";
                if (payload instanceof Map<?, ?> map) {
                    if (map.get("alarmType") != null) alarmType = map.get("alarmType").toString();
                    if (map.get("severity") != null) severity = map.get("severity").toString();
                    if (map.get("message") != null) message = map.get("message").toString();
                }
                alertService.createNotification(
                        alarmType,
                        null,
                        message,
                        severity,
                        null,
                        aggId,
                        fieldId,
                        null,
                        null,
                        "SYSTEM",
                        null,
                        aggId,
                        null,
                        "ALARM:" + aggId + ":" + alarmType,
                        30,
                        null
                );
            }
            case EMERGENCY_STOP_ACTIVATED -> {
                String reason = "Emergency stop activated";
                if (payload instanceof Map<?, ?> map && map.get("reason") != null) {
                    reason = map.get("reason").toString();
                }
                alertService.createNotification(
                        "EMERGENCY_STOP_ACTIVATED",
                        "Emergency Stop Activated",
                        "Emergency irrigation stop triggered: " + reason,
                        "CRITICAL",
                        "irrigation",
                        null,
                        fieldId,
                        null,
                        null,
                        "OPERATOR",
                        "Centralized Irrigation System",
                        "ENTIRE FIELD",
                        "Inspect field valves and clear lockout from Irrigation Control before resuming.",
                        "EMERGENCY_STOP:" + aggId,
                        1,
                        null
                );
            }
            case IRRIGATION_DECISION_MADE -> {
                String nodeId = aggId;
                String msg = "Autonomous irrigation decision recorded.";
                if (payload instanceof IrrigationDecisionResponse dec) {
                    nodeId = dec.getNodeId() != null ? dec.getNodeId() : aggId;
                    msg = String.format("Node %s reported %s decision (status: %s, duration: %s min).",
                            nodeId,
                            dec.getDecisionType() != null ? dec.getDecisionType() : "IRRIGATION",
                            dec.getExecutionStatus(),
                            dec.getRequestedDurationMinutes() != null ? dec.getRequestedDurationMinutes() : 0);
                }
                alertService.createNotification(
                        "IRRIGATION_DECISION_MADE",
                        "Irrigation Decision Recorded",
                        msg,
                        "INFO",
                        "irrigation",
                        nodeId,
                        fieldId,
                        null,
                        null,
                        "EDGE_NODE",
                        "Centralized Irrigation System",
                        "ENTIRE FIELD",
                        "Review irrigation execution metrics on the Irrigation Control screen.",
                        null,
                        0,
                        null
                );
            }
            case IRRIGATION_EXECUTION_UPDATED -> {
                String cmdType = "IRRIGATION_UPDATED";
                String title = "Irrigation Execution Updated";
                String msg = "Irrigation command status updated.";
                String actor = "OPERATOR";
                if (payload instanceof CommandStatusResponse cmd) {
                    cmdType = cmd.getCommandType() != null ? cmd.getCommandType() : "IRRIGATION_COMMAND";
                    actor = cmd.getOperatorId() != null ? cmd.getOperatorId() : "OPERATOR";
                    if ("MANUAL_START".equalsIgnoreCase(cmdType)) {
                        title = "Manual Irrigation Started";
                        msg = String.format("Manual irrigation started by %s (%s).",
                                actor, cmd.getRationale() != null ? cmd.getRationale() : "Scheduled run");
                    } else if ("MANUAL_STOP".equalsIgnoreCase(cmdType)) {
                        title = "Manual Irrigation Stopped";
                        msg = String.format("Manual irrigation stopped by %s (%s).",
                                actor, cmd.getRationale() != null ? cmd.getRationale() : "Operator stop");
                    } else {
                        msg = cmd.getDetails() != null ? cmd.getDetails() : msg;
                    }
                }
                alertService.createNotification(
                        cmdType,
                        title,
                        msg,
                        "INFO",
                        "irrigation",
                        null,
                        fieldId,
                        null,
                        null,
                        actor,
                        "Centralized Irrigation System",
                        "ENTIRE FIELD",
                        "Monitor active field irrigation on the Irrigation Control screen.",
                        "IRRIG_EXEC:" + aggId + ":" + cmdType,
                        2,
                        null
                );
            }
            case CONFIG_SYNCED -> {
                String msg = "Irrigation configuration and thresholds updated for Field " + (fieldId != null ? fieldId : "1") + ".";
                if (payload instanceof AutoIrrigationConfigResponse cfg) {
                    msg = String.format("Irrigation configuration v%s synced (enabled=%s, targetDepth=%scm).",
                            cfg.getConfigVersion(), cfg.getEnabled(), cfg.getTargetFloodDepthCm());
                }
                alertService.createNotification(
                        "IRRIGATION_CONFIG_UPDATED",
                        "Irrigation Configuration Updated",
                        msg,
                        "INFO",
                        "irrigation",
                        null,
                        fieldId,
                        null,
                        null,
                        "OPERATOR",
                        "Centralized Irrigation System",
                        "ENTIRE FIELD",
                        "Verify updated thresholds in Irrigation Settings.",
                        null,
                        0,
                        null
                );
            }
            case MASTER_STATE_CHANGED -> {
                boolean enabled = true;
                String updatedBy = "SYSTEM";
                if (payload instanceof Map<?, ?> map) {
                    if (map.get("masterEnabled") instanceof Boolean b) enabled = b;
                    if (map.get("updatedBy") != null) updatedBy = map.get("updatedBy").toString();
                }
                alertService.createNotification(
                        "MASTER_STATE_CHANGED",
                        enabled ? "Master System Enabled" : "Master System Disabled",
                        String.format("ArvaCore master system state turned %s by %s.", enabled ? "ON" : "OFF", updatedBy),
                        enabled ? "INFO" : "WARNING",
                        "hardware",
                        null,
                        fieldId,
                        null,
                        null,
                        updatedBy,
                        "Centralized Irrigation System",
                        "ENTIRE FIELD",
                        enabled
                                ? "Telemetry ingestion and node pairing are active."
                                : "Enable Master System State in Settings to resume telemetry ingestion.",
                        null,
                        0,
                        null
                );
            }
            default -> {}
        }
    }
}

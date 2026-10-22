package com.aquaflow.backend.infrastructure.mqtt;

import com.aquaflow.backend.domain.AlertService;
import com.aquaflow.backend.domain.SensorDataService;
import com.aquaflow.backend.domain.TelemetryIngestionService;
import com.aquaflow.backend.dto.request.SensorDataRequest;
import com.aquaflow.backend.dto.request.TelemetryUplinkRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class MqttMessageListener implements MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(MqttMessageListener.class);

    private final SensorDataService sensorDataService;
    private final TelemetryIngestionService telemetryIngestionService;
    private final ObjectMapper objectMapper;
    private final AlertService alertService;

    private MqttClient mqttClient;
    private ExecutorService executor;

    @Autowired
    public MqttMessageListener(
            SensorDataService sensorDataService,
            @Autowired(required = false) TelemetryIngestionService telemetryIngestionService,
            ObjectMapper objectMapper,
            @Autowired(required = false) AlertService alertService
    ) {
        this.sensorDataService = sensorDataService;
        this.telemetryIngestionService = telemetryIngestionService;
        this.objectMapper = objectMapper;
        this.alertService = alertService;
    }

    @PostConstruct
    public void init() {
        executor = Executors.newFixedThreadPool(4);
        try {
            String broker = System.getenv().getOrDefault("MQTT_BROKER_URL", "tcp://localhost:1883");
            mqttClient = new MqttClient(broker, "aquaflow-backend-listener", new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);

            String user = System.getenv("MQTT_USERNAME");
            String pass = System.getenv("MQTT_PASSWORD");
            if (user != null && !user.isBlank()) {
                options.setUserName(user);
                options.setPassword(pass != null ? pass.toCharArray() : new char[0]);
            }

            mqttClient.setCallback(this);
            mqttClient.connect(options);

            // Subscriptions covering raw sensor data, device telemetry, and events
            mqttClient.subscribe("node/+/telemetry", 0);
            mqttClient.subscribe("node/+/event", 1);
            mqttClient.subscribe("sensor/+/data", 0);
            mqttClient.subscribe("device/+/status", 1);
            mqttClient.subscribe("device/+/alert", 1);

            log.info("MQTT listener connected to {} and subscribed to topics", broker);
        } catch (Exception e) {
            log.warn("MQTT connection initialization skipped or failed (will retry if broker available): {}", e.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
            if (executor != null) {
                executor.shutdown();
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost: {}", cause != null ? cause.getMessage() : "unknown");
        reconnect();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        executor.submit(() -> {
            try {
                String payload = new String(message.getPayload());
                log.debug("MQTT message received [topic={}, qos={}]: {}", topic, message.getQos(), payload);
                processMessage(topic, payload);
            } catch (Exception e) {
                log.error("Error processing MQTT message on topic {}", topic, e);
            }
        });
    }

    private void processMessage(String topic, String payload) {
        String[] parts = topic.split("/");
        if (parts.length < 3) return;

        String root = parts[0];
        String nodeIdOrDevice = parts[1];
        String sub = parts[2];

        // 1. JSON Telemetry Uplink via MQTT: node/{nodeId}/telemetry
        if ("node".equals(root) && "telemetry".equals(sub)) {
            handleNodeTelemetry(nodeIdOrDevice, payload);
            return;
        }

        // 2. Node Event via MQTT: node/{nodeId}/event or device/{id}/alert
        if (("node".equals(root) && "event".equals(sub)) || ("device".equals(root) && "alert".equals(sub))) {
            handleNodeEvent(nodeIdOrDevice, payload);
            return;
        }

        // 3. Raw sensor value fallback: sensor/{deviceId}/data
        if ("sensor".equals(root) && "data".equals(sub)) {
            SensorDataRequest request = new SensorDataRequest();
            request.setDeviceId(nodeIdOrDevice);
            request.setSensorType(parts.length > 3 ? parts[3] : "soil_moisture");
            try {
                request.setValue(Double.parseDouble(payload.trim()));
                sensorDataService.ingestSensorData(request);
            } catch (NumberFormatException e) {
                // Try JSON format
                try {
                    TelemetryUplinkRequest uplink = objectMapper.readValue(payload, TelemetryUplinkRequest.class);
                    if (telemetryIngestionService != null) {
                        telemetryIngestionService.ingestUplinkForNode(nodeIdOrDevice, uplink);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private void handleNodeTelemetry(String nodeId, String payload) {
        if (telemetryIngestionService == null) return;
        try {
            TelemetryUplinkRequest uplink = objectMapper.readValue(payload, TelemetryUplinkRequest.class);
            if (uplink.getNodeId() == null || uplink.getNodeId().isBlank()) {
                uplink.setNodeId(nodeId);
            }
            telemetryIngestionService.ingestUplinkForNode(nodeId, uplink);
        } catch (Exception e) {
            log.warn("Failed to parse node telemetry from MQTT for node {}: {}", nodeId, e.getMessage());
        }
    }

    private void handleNodeEvent(String nodeId, String payload) {
        if (alertService == null) return;
        try {
            JsonNode node = objectMapper.readTree(payload);
            String title = node.has("title") ? node.get("title").asText() : "Device Alert: " + nodeId;
            String message = node.has("message") ? node.get("message").asText() : payload;
            String severity = node.has("severity") ? node.get("severity").asText() : "WARNING";
            String eventType = node.has("eventType") ? node.get("eventType").asText() : "HARDWARE_ALERT";

            alertService.createNotification(
                    eventType,
                    title,
                    message,
                    severity,
                    "hardware",
                    nodeId,
                    null,
                    null,
                    null,
                    "DEVICE_MQTT",
                    "Node " + nodeId,
                    nodeId,
                    "Review device status and connectivity.",
                    "MQTT_EVENT:" + nodeId + ":" + eventType,
                    30,
                    payload
            );
        } catch (Exception e) {
            log.warn("Failed to process node event from MQTT: {}", e.getMessage());
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        log.debug("MQTT delivery complete");
    }

    public void reconnect() {
        if (executor == null || executor.isShutdown()) return;
        executor.submit(() -> {
            try {
                if (mqttClient != null && !mqttClient.isConnected()) {
                    mqttClient.reconnect();
                    log.info("MQTT reconnected successfully");
                }
            } catch (Exception e) {
                log.warn("MQTT reconnect attempt failed: {}", e.getMessage());
            }
        });
    }
}

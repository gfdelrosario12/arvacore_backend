package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.ArvaGateAckRequest;
import com.aquaflow.backend.dto.request.ArvaGateDispatchCommandRequest;
import com.aquaflow.backend.dto.request.ArvaGateTelemetryRequest;
import com.aquaflow.backend.dto.response.ArvaGateStatusResponse;
import com.aquaflow.backend.entity.ArvaGate;
import com.aquaflow.backend.entity.ArvaGateCommand;
import com.aquaflow.backend.entity.ArvaGateDevice;
import com.aquaflow.backend.entity.ArvaGateEvent;
import com.aquaflow.backend.persistence.ArvaGateCommandRepository;
import com.aquaflow.backend.persistence.ArvaGateDeviceRepository;
import com.aquaflow.backend.persistence.ArvaGateEventRepository;
import com.aquaflow.backend.persistence.ArvaGateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ArvaGateService {

    private static final Logger log = LoggerFactory.getLogger(ArvaGateService.class);

    private final ArvaGateDeviceRepository deviceRepository;
    private final ArvaGateRepository gateRepository;
    private final ArvaGateCommandRepository commandRepository;
    private final ArvaGateEventRepository eventRepository;

    public ArvaGateService(ArvaGateDeviceRepository deviceRepository,
                           ArvaGateRepository gateRepository,
                           ArvaGateCommandRepository commandRepository,
                           ArvaGateEventRepository eventRepository) {
        this.deviceRepository = deviceRepository;
        this.gateRepository = gateRepository;
        this.commandRepository = commandRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public ArvaGateStatusResponse getStatus(String deviceId) {
        ArvaGateDevice device = deviceRepository.findByDeviceId(deviceId)
                .orElseGet(() -> ArvaGateDevice.builder()
                        .deviceId(deviceId)
                        .displayName("ArvaGate Canal Controller")
                        .connectivityStatus("UNKNOWN")
                        .build());

        List<ArvaGate> gates = gateRepository.findByDeviceIdOrderByGateIndexAsc(deviceId);
        List<ArvaGateEvent> events = eventRepository.findTop50ByDeviceIdOrderByCreatedAtDesc(deviceId);

        return ArvaGateStatusResponse.builder()
                .device(device)
                .gates(gates)
                .recentEvents(events)
                .serverTime(LocalDateTime.now())
                .build();
    }

    @Transactional
    public ArvaGateCommand dispatchCommand(String deviceId, ArvaGateDispatchCommandRequest request, String actor) {
        log.info("Dispatching ArvaGate command for device: {}, action: {}, targets: {}, actor: {}",
                deviceId, request.getAction(), request.getTargetGates(), actor);

        String commandId = "CMD-GATE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDateTime now = LocalDateTime.now();

        ArvaGateCommand command = ArvaGateCommand.builder()
                .commandId(commandId)
                .deviceId(deviceId)
                .targetGates(request.getTargetGates())
                .action(request.getAction().toUpperCase())
                .durationSeconds(request.getDurationSeconds() != null ? request.getDurationSeconds() : 0)
                .issuedBy(actor != null ? actor : "OPERATOR")
                .status("PENDING")
                .expiresAt(now.plusMinutes(10))
                .build();

        ArvaGateCommand saved = commandRepository.save(command);

        // Update requestedState on target gates
        List<ArvaGate> gates = gateRepository.findByDeviceIdOrderByGateIndexAsc(deviceId);
        for (ArvaGate gate : gates) {
            String idxStr = String.valueOf(gate.getGateIndex());
            if (request.getTargetGates().contains(idxStr) || "ALL".equalsIgnoreCase(request.getTargetGates())) {
                gate.setRequestedState(request.getAction().toUpperCase());
                gateRepository.save(gate);

                eventRepository.save(ArvaGateEvent.builder()
                        .commandId(commandId)
                        .deviceId(deviceId)
                        .gateIndex(gate.getGateIndex())
                        .eventType("COMMAND_DISPATCHED")
                        .previousState(gate.getCurrentState())
                        .newState(request.getAction().toUpperCase())
                        .details(request.getRationale() != null ? request.getRationale() : "Manual gate control command")
                        .actor(actor != null ? actor : "OPERATOR")
                        .build());
            }
        }

        return saved;
    }

    @Transactional
    public ArvaGateCommand getPendingCommand(String deviceId) {
        List<ArvaGateCommand> pending = commandRepository.findByDeviceIdAndStatusOrderByCreatedAtAsc(deviceId, "PENDING");
        LocalDateTime now = LocalDateTime.now();

        for (ArvaGateCommand cmd : pending) {
            if (cmd.getExpiresAt() != null && now.isAfter(cmd.getExpiresAt())) {
                cmd.setStatus("TIMEOUT");
                commandRepository.save(cmd);
                continue;
            }
            return cmd;
        }
        return null;
    }

    @Transactional
    public void handleAcknowledgement(String commandId, ArvaGateAckRequest ack) {
        log.info("Handling ArvaGate ACK for command: {}, status: {}", commandId, ack.getStatus());

        commandRepository.findByCommandId(commandId).ifPresent(cmd -> {
            cmd.setStatus(ack.getStatus());
            cmd.setExecutedAt(LocalDateTime.now());
            if (ack.getDetails() != null) {
                cmd.setErrorDetails(ack.getDetails());
            }
            commandRepository.save(cmd);
        });

        // Update device last seen
        deviceRepository.findByDeviceId(ack.getDeviceId()).ifPresent(dev -> {
            dev.setLastSeenAt(LocalDateTime.now());
            dev.setConnectivityStatus("ONLINE");
            deviceRepository.save(dev);
        });

        // Update gates if state feedback is provided
        if (ack.getGate1State() != null) updateGateState(ack.getDeviceId(), 1, ack.getGate1State());
        if (ack.getGate2State() != null) updateGateState(ack.getDeviceId(), 2, ack.getGate2State());
        if (ack.getGate3State() != null) updateGateState(ack.getDeviceId(), 3, ack.getGate3State());

        eventRepository.save(ArvaGateEvent.builder()
                .commandId(commandId)
                .deviceId(ack.getDeviceId())
                .eventType("COMMAND_ACK")
                .details("Status: " + ack.getStatus() + (ack.getDetails() != null ? " (" + ack.getDetails() + ")" : ""))
                .actor("ARVAGATE_HARDWARE")
                .build());
    }

    @Transactional
    public void handleTelemetry(String deviceId, ArvaGateTelemetryRequest telemetry) {
        deviceRepository.findByDeviceId(deviceId).ifPresent(dev -> {
            dev.setLastSeenAt(LocalDateTime.now());
            dev.setConnectivityStatus("ONLINE");
            deviceRepository.save(dev);
        });

        if (telemetry.getGates() != null) {
            for (ArvaGateTelemetryRequest.GateTelemetryItem item : telemetry.getGates()) {
                if (item.getGateIndex() == null) continue;
                gateRepository.findByDeviceIdAndGateIndex(deviceId, item.getGateIndex()).ifPresent(g -> {
                    if (item.getState() != null) g.setCurrentState(item.getState());
                    if (item.getPhysicalConfirmed() != null) g.setPhysicalConfirmed(item.getPhysicalConfirmed());
                    g.setLastActuatedAt(LocalDateTime.now());
                    gateRepository.save(g);
                });
            }
        }
    }

    private void updateGateState(String deviceId, int index, String state) {
        gateRepository.findByDeviceIdAndGateIndex(deviceId, index).ifPresent(g -> {
            g.setCurrentState(state);
            g.setLastActuatedAt(LocalDateTime.now());
            gateRepository.save(g);
        });
    }
}


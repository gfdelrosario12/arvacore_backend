package com.aquaflow.backend.arvacore;

import com.aquaflow.backend.domain.JsonPayloadDecoder;
import com.aquaflow.backend.domain.TelemetryValidator;
import com.aquaflow.backend.dto.DecodedTelemetryPayload;
import com.aquaflow.backend.dto.request.TelemetryUplinkRequest;
import com.aquaflow.backend.entity.SensorType;
import com.aquaflow.backend.infrastructure.security.GatewayAuthenticationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Requirement traceability: openspec change integrate-arvacore-ecosystem, spec arvacore-telemetry-integrity. */
class ArvaCoreTelemetryIntegrityTest {

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void decoderDoesNotFabricateMissingValues() {
        TelemetryUplinkRequest req = new TelemetryUplinkRequest();
        req.setNodeId("ARVANODE-01");
        DecodedTelemetryPayload p = new JsonPayloadDecoder().decode(req);
        assertTrue(p.getMeasurements().isEmpty(), "heartbeat must not invent soil moisture");
        assertNull(p.getBatteryLevel());
        assertNull(p.getSolarVoltage());
        assertNull(p.getSignalMetadata().getRssiDbm());
        assertNull(p.getSignalMetadata().getSnrDb());
    }

    @Test
    void decoderAcceptsUltrasonicDistance() {
        TelemetryUplinkRequest req = new TelemetryUplinkRequest();
        req.setNodeId("ARVANODE-01");
        req.setMeasurements(Map.of("water_level_distance", 42.5, "unknown_metric", 1.0));
        DecodedTelemetryPayload p = new JsonPayloadDecoder().decode(req);
        assertEquals(42.5, p.getMeasurements().get(SensorType.WATER_LEVEL_DISTANCE));
        assertEquals(1, p.getMeasurements().size());
    }

    @Test
    void validatorEnforcesSensorPhysicalLimits() {
        assertTrue(TelemetryValidator.isPlausible(SensorType.WATER_LEVEL_DISTANCE, 20.0));
        assertTrue(TelemetryValidator.isPlausible(SensorType.WATER_LEVEL_DISTANCE, 5.0));
        assertTrue(TelemetryValidator.isPlausible(SensorType.WATER_LEVEL_DISTANCE, 0.0));
        assertFalse(TelemetryValidator.isPlausible(SensorType.WATER_LEVEL_DISTANCE, 1.5));
        assertFalse(TelemetryValidator.isPlausible(SensorType.WATER_LEVEL_DISTANCE, 451.0));
        assertFalse(TelemetryValidator.isPlausible(SensorType.SOIL_MOISTURE, 120.0));
        assertFalse(TelemetryValidator.isPlausible(SensorType.HUMIDITY, -1.0));
        assertFalse(TelemetryValidator.isPlausible(SensorType.TEMPERATURE, Double.NaN));
        assertFalse(TelemetryValidator.isPlausible(SensorType.WATER_FLOW, null));
        assertTrue(TelemetryValidator.isPlausible(SensorType.WATER_FLOW, 0.0));
        assertFalse(TelemetryValidator.isHealthyMeasurement(SensorType.WATER_LEVEL_DISTANCE, 0.0, "TIMEOUT"));
        assertFalse(TelemetryValidator.isHealthyMeasurement(SensorType.SOIL_MOISTURE, 0.0, "DISCONNECTED"));
        assertTrue(TelemetryValidator.isHealthyMeasurement(SensorType.SOIL_MOISTURE, 0.0, "OK"));
    }

    private GatewayAuthenticationFilter filter(String key) {
        GatewayAuthenticationFilter f = new GatewayAuthenticationFilter();
        ReflectionTestUtils.setField(f, "configuredKey", key);
        return f;
    }

    @Test
    void gatewayWithValidKeyIsAuthenticated() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/telemetry/uplink");
        req.addHeader(GatewayAuthenticationFilter.HEADER, "secret-key");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter("secret-key").doFilter(req, res, chain);
        assertEquals(200, res.getStatus());
        assertNotNull(chain.getRequest());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_GATEWAY")));
    }

    @Test
    void gatewayWithWrongKeyIsRejected() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/telemetry/uplink");
        req.addHeader(GatewayAuthenticationFilter.HEADER, "wrong");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter("secret-key").doFilter(req, res, chain);
        assertEquals(401, res.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void gatewayKeyRejectedWhenServerKeyNotConfigured() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/telemetry/uplink");
        req.addHeader(GatewayAuthenticationFilter.HEADER, "");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter("").doFilter(req, res, new MockFilterChain());
        assertEquals(401, res.getStatus());
    }

    @Test
    void requestsWithoutGatewayHeaderFallThroughToJwt() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/telemetry/uplink");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter("secret-key").doFilter(req, res, chain);
        assertNotNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}


package com.aquaflow.backend.infrastructure.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemEvent {

    @Builder.Default
    private Integer version = 1;

    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    private SystemEventType eventType;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    private LocalDateTime occurredAt;

    private String aggregateId;

    private String scope;

    @Builder.Default
    private Long sequence = 0L;

    private Long fieldId;

    private Object payload;

    public Integer getVersion() { return version != null ? version : 1; }
    public void setVersion(Integer version) { this.version = version; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public SystemEventType getEventType() { return eventType; }
    public void setEventType(SystemEventType eventType) { this.eventType = eventType; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public LocalDateTime getOccurredAt() { return occurredAt != null ? occurredAt : timestamp; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public String getScope() { return scope != null ? scope : (aggregateId != null ? aggregateId : "SYSTEM"); }
    public void setScope(String scope) { this.scope = scope; }
    public Long getSequence() { return sequence != null ? sequence : 0L; }
    public void setSequence(Long sequence) { this.sequence = sequence; }
    public Long getFieldId() { return fieldId; }
    public void setFieldId(Long fieldId) { this.fieldId = fieldId; }
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
}


package com.aquaflow.backend.domain;

import com.aquaflow.backend.dto.request.IrrigationScheduleRequest;
import com.aquaflow.backend.dto.response.IrrigationScheduleResponse;
import com.aquaflow.backend.entity.IrrigationSchedule;
import com.aquaflow.backend.entity.Zone;
import com.aquaflow.backend.infrastructure.exception.ResourceNotFoundException;
import com.aquaflow.backend.persistence.IrrigationScheduleRepository;
import com.aquaflow.backend.persistence.ZoneRepository;
import com.aquaflow.backend.infrastructure.util.DtoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class IrrigationScheduleServiceImpl implements IrrigationScheduleService {

    private static final Logger log = LoggerFactory.getLogger(IrrigationScheduleServiceImpl.class);

    private final IrrigationScheduleRepository scheduleRepository;
    private final ZoneRepository zoneRepository;
    private AlertService alertService;

    public IrrigationScheduleServiceImpl(IrrigationScheduleRepository scheduleRepository, ZoneRepository zoneRepository) {
        this.scheduleRepository = scheduleRepository;
        this.zoneRepository = zoneRepository;
    }

    @Autowired(required = false)
    public void setAlertService(AlertService alertService) {
        this.alertService = alertService;
    }

    @Override
    public IrrigationScheduleResponse createSchedule(IrrigationScheduleRequest request) {
        log.info("Creating irrigation schedule for zone: {}", request.getZoneId());

        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found with id: " + request.getZoneId()));

        IrrigationSchedule schedule = IrrigationSchedule.builder()
                .zone(zone)
                .startTime(request.getStartTime())
                .duration(request.getDuration())
                .waterVolume(request.getWaterVolume())
                .recurrenceRule(request.getRecurrenceRule())
                .status("SCHEDULED")
                .build();

        IrrigationSchedule saved = scheduleRepository.save(schedule);
        log.info("Schedule created with id: {}", saved.getId());
        if (alertService != null) {
            alertService.createNotification(
                    "IRRIGATION_SCHEDULE_CREATED",
                    "Irrigation Schedule Created",
                    "Created irrigation schedule for zone " + zone.getName() + " (Duration: " + request.getDuration() + " min).",
                    "INFO",
                    "Irrigation",
                    null,
                    null,
                    zone.getId(),
                    zone.getName(),
                    "OPERATOR",
                    "Irrigation Schedule Service",
                    "ZONE",
                    "Review upcoming irrigation cycles in the Field Dashboard.",
                    null,
                    0,
                    null
            );
        }
        return DtoMapper.toScheduleResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public IrrigationScheduleResponse getScheduleById(Long id) {
        IrrigationSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found with id: " + id));
        return DtoMapper.toScheduleResponse(schedule);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IrrigationScheduleResponse> getAllSchedules(Pageable pageable) {
        return scheduleRepository.findAll(pageable).map(DtoMapper::toScheduleResponse);
    }

    @Override
    public IrrigationScheduleResponse updateSchedule(Long id, IrrigationScheduleRequest request) {
        IrrigationSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found with id: " + id));

        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found with id: " + request.getZoneId()));

        schedule.setZone(zone);
        schedule.setStartTime(request.getStartTime());
        schedule.setDuration(request.getDuration());
        schedule.setWaterVolume(request.getWaterVolume());
        schedule.setRecurrenceRule(request.getRecurrenceRule());

        IrrigationSchedule saved = scheduleRepository.save(schedule);
        log.info("Schedule updated with id: {}", id);
        if (alertService != null) {
            alertService.createNotification(
                    "IRRIGATION_SCHEDULE_UPDATED",
                    "Irrigation Schedule Updated",
                    "Updated irrigation schedule #" + id + " for zone " + zone.getName() + ".",
                    "INFO",
                    "Irrigation",
                    null,
                    null,
                    zone.getId(),
                    zone.getName(),
                    "OPERATOR",
                    "Irrigation Schedule Service",
                    "ZONE",
                    "Verify the updated irrigation schedule parameters.",
                    null,
                    0,
                    null
            );
        }
        return DtoMapper.toScheduleResponse(saved);
    }

    @Override
    public void deleteSchedule(Long id) {
        if (!scheduleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Schedule not found with id: " + id);
        }
        scheduleRepository.deleteById(id);
        log.info("Schedule deleted with id: {}", id);
        if (alertService != null) {
            alertService.createNotification(
                    "IRRIGATION_SCHEDULE_DELETED",
                    "Irrigation Schedule Removed",
                    "Deleted irrigation schedule #" + id + ".",
                    "INFO",
                    "Irrigation",
                    null,
                    null,
                    null,
                    null,
                    "OPERATOR",
                    "Irrigation Schedule Service",
                    "ZONE",
                    "No further action required.",
                    null,
                    0,
                    null
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<IrrigationScheduleResponse> getSchedulesByZoneId(Long zoneId) {
        return scheduleRepository.findByZoneIdOrderByStartTimeAsc(zoneId).stream()
                .map(DtoMapper::toScheduleResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<IrrigationScheduleResponse> getSchedulesByZoneIdOrderByStartTimeAsc(Long zoneId) {
        return scheduleRepository.findByZoneIdOrderByStartTimeAsc(zoneId).stream()
                .map(DtoMapper::toScheduleResponse)
                .collect(Collectors.toList());
    }
}
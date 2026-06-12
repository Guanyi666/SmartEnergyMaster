package com.smartenergy.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartenergy.backend.annotation.DistributedLock;
import com.smartenergy.backend.annotation.RateLimit;
import com.smartenergy.backend.dto.SensorDataDTO;
import com.smartenergy.backend.entity.SensorData;
import com.smartenergy.backend.service.SensorDataService;
import com.smartenergy.backend.vo.PageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/sensor")
@RequiredArgsConstructor
@Tag(name = "Sensor Data", description = "Sensor data upload and query APIs")
public class SensorDataController {

    private final SensorDataService sensorDataService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @PostMapping("/upload")
    @Operation(summary = "Upload sensor data", description = "Accepts either one JSON object or a JSON array.")
    @RateLimit(name = "upload", limit = 100, window = 1, dimension = RateLimit.Dimension.SPEL,
            key = "#p0.toString().hashCode()", message = "Sensor upload rate limit exceeded")
    @DistributedLock(name = "upload", key = "#p0.toString().hashCode()", waitMillis = 200, leaseMillis = 5000,
            message = "Same sensor payload is being processed")
    public ResponseEntity<String> uploadSensorData(@RequestBody JsonNode payload) {
        List<SensorDataDTO> sensorDataList = parsePayload(payload);
        sensorDataService.uploadBatch(sensorDataList);
        return ResponseEntity.ok("Sensor data received");
    }

    @GetMapping("/latest/{deviceCode}")
    @Operation(summary = "Get latest sensor data", description = "Returns the latest sensor record for a device.")
    public ResponseEntity<?> getLatestData(
            @Parameter(description = "Device code") @PathVariable String deviceCode) {
        SensorData data = sensorDataService.getLatestData(deviceCode);
        if (data == null) {
            return ResponseEntity.ok("No sensor data for this device");
        }
        return ResponseEntity.ok(data);
    }

    @GetMapping("/history/{deviceCode}")
    @Operation(summary = "Get sensor history", description = "Returns sensor history in a time window. Add page/size for pagination.")
    public ResponseEntity<?> getHistoryData(
            @Parameter(description = "Device code") @PathVariable String deviceCode,
            @Parameter(description = "Last N hours") @RequestParam(defaultValue = "24") int hours,
            @Parameter(description = "Page number, starts from 1") @RequestParam(required = false) Long page,
            @Parameter(description = "Page size") @RequestParam(required = false) Long size) {
        if (page != null || size != null) {
            PageVO<SensorData> result = sensorDataService.getHistoryData(
                    deviceCode,
                    hours,
                    page == null ? 1L : page,
                    size == null ? 50L : size);
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(sensorDataService.getHistoryData(deviceCode, hours));
    }

    private List<SensorDataDTO> parsePayload(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException("Sensor payload cannot be empty");
        }

        List<SensorDataDTO> result = new ArrayList<>();
        if (payload.isArray()) {
            payload.forEach(node -> result.add(toDto(node)));
        } else if (payload.isObject()) {
            result.add(toDto(payload));
        } else {
            throw new IllegalArgumentException("Sensor payload must be a JSON object or array");
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException("Sensor payload cannot be empty");
        }
        return result;
    }

    private SensorDataDTO toDto(JsonNode node) {
        SensorDataDTO dto = objectMapper.convertValue(node, SensorDataDTO.class);
        Set<ConstraintViolation<SensorDataDTO>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.iterator().next().getMessage());
        }
        return dto;
    }
}

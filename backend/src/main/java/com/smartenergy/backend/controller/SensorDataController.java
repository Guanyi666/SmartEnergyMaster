package com.smartenergy.backend.controller;

import com.smartenergy.backend.dto.SensorDataDTO;
import com.smartenergy.backend.entity.SensorData;
import com.smartenergy.backend.service.SensorDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sensor")
@RequiredArgsConstructor
public class SensorDataController {

    private final SensorDataService sensorDataService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadSensorData(@Validated @RequestBody SensorDataDTO sensorDataDTO) {
        sensorDataService.uploadData(sensorDataDTO);
        return ResponseEntity.ok("数据接收成功");
    }

    @GetMapping("/latest/{deviceCode}")
    public ResponseEntity<?> getLatestData(@PathVariable String deviceCode) {
        SensorData data = sensorDataService.getLatestData(deviceCode);
        if (data == null) {
            return ResponseEntity.ok("暂无该设备数据");
        }
        return ResponseEntity.ok(data);
    }

    @GetMapping("/history/{deviceCode}")
    public ResponseEntity<List<SensorData>> getHistoryData(
            @PathVariable String deviceCode,
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(sensorDataService.getHistoryData(deviceCode, hours));
    }
}

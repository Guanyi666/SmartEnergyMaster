package com.smartenergy.backend.controller;

import com.smartenergy.backend.annotation.DistributedLock;
import com.smartenergy.backend.annotation.RateLimit;
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
    @RateLimit(name = "upload", limit = 100, window = 1, dimension = RateLimit.Dimension.SPEL,
            key = "#sensorDataDTO.deviceCode", message = "设备上报频率超限")
    @DistributedLock(name = "upload", key = "#sensorDataDTO.deviceCode", waitMillis = 200, leaseMillis = 3000)
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

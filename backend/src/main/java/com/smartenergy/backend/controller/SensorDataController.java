package com.smartenergy.backend.controller;

import com.smartenergy.backend.dto.SensorDataDTO;
import com.smartenergy.backend.entity.SensorData;
import com.smartenergy.backend.service.SensorDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sensor")
@RequiredArgsConstructor
@Tag(name = "传感器数据", description = "传感器数据上报与查询")
public class SensorDataController {

    private final SensorDataService sensorDataService;

    @PostMapping("/upload")
    @Operation(summary = "上报传感器数据", description = "接收设备遥测数据（功耗、温度、振动、压力、CO₂排放、电价时段等），数据上传后自动触发故障检测")
    public ResponseEntity<String> uploadSensorData(@Validated @RequestBody SensorDataDTO sensorDataDTO) {
        sensorDataService.uploadData(sensorDataDTO);
        return ResponseEntity.ok("数据接收成功");
    }

    @GetMapping("/latest/{deviceCode}")
    @Operation(summary = "查询最新数据", description = "返回指定设备最近一条传感器记录")
    public ResponseEntity<?> getLatestData(
            @Parameter(description = "设备编码") @PathVariable String deviceCode) {
        SensorData data = sensorDataService.getLatestData(deviceCode);
        if (data == null) {
            return ResponseEntity.ok("暂无该设备数据");
        }
        return ResponseEntity.ok(data);
    }

    @GetMapping("/history/{deviceCode}")
    @Operation(summary = "查询历史数据", description = "返回指定设备在指定时间窗口内的时序数据，供前端绘制趋势图")
    public ResponseEntity<List<SensorData>> getHistoryData(
            @Parameter(description = "设备编码") @PathVariable String deviceCode,
            @Parameter(description = "查询最近 N 小时的数据") @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(sensorDataService.getHistoryData(deviceCode, hours));
    }
}

package com.smartenergy.backend.controller;

import com.smartenergy.backend.dto.SensorDataDTO;
import com.smartenergy.backend.entity.SensorData;
import com.smartenergy.backend.service.SensorDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author Duan Guanyi
 * @version 1.0.0
 * @date 2026/3/19
 */
@RestController
@RequestMapping("/api/sensor")
public class SensorDataController {

    @Autowired
    private SensorDataService sensorDataService;

    /**
     * 接收 Python 数据泵/硬件网关的高频数据上报
     */
    @PostMapping("/upload")
    public ResponseEntity<String> uploadSensorData(@Validated @RequestBody SensorDataDTO sensorDataDTO) {
        try {
            sensorDataService.uploadData(sensorDataDTO);
            return ResponseEntity.ok("数据接收成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("数据接收失败：" + e.getMessage());
        }
    }

    /**
     * 获取设备最新状态
     */
    @GetMapping("/latest/{deviceCode}")
    public ResponseEntity<?> getLatestData(@PathVariable String deviceCode) {
        try {
            SensorData data = sensorDataService.getLatestData(deviceCode);
            if (data == null) {
                return ResponseEntity.ok("暂无该设备的数据");
            }
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 获取设备 24 小时历史负荷曲线
     */
    @GetMapping("/history/{deviceCode}")
    public ResponseEntity<?> getHistoryData(
            @PathVariable String deviceCode,
            @RequestParam(defaultValue = "24") int hours) {
        try {
            List<SensorData> dataList = sensorDataService.getHistoryData(deviceCode, hours);
            return ResponseEntity.ok(dataList);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}

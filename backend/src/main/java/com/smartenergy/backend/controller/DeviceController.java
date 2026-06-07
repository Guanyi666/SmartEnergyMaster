package com.smartenergy.backend.controller;

import com.smartenergy.backend.dto.DeviceUpsertRequest;
import com.smartenergy.backend.entity.Device;
import com.smartenergy.backend.service.DeviceService;
import com.smartenergy.backend.vo.DeviceOverviewVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    public List<DeviceOverviewVO> listDevices() {
        return deviceService.listDevices();
    }

    @PostMapping
    public Device createDevice(@Valid @RequestBody DeviceUpsertRequest request) {
        return deviceService.createDevice(request);
    }

    @PutMapping("/{id}")
    public Device updateDevice(@PathVariable Integer id, @Valid @RequestBody DeviceUpsertRequest request) {
        return deviceService.updateDevice(id, request);
    }

    @DeleteMapping("/{id}")
    public void deleteDevice(@PathVariable Integer id) {
        deviceService.deleteDevice(id);
    }
}

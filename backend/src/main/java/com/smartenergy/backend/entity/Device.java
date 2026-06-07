package com.smartenergy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device")
public class Device {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String deviceCode;

    private String deviceName;

    private String deviceType;

    private String status;

    private String location;

    private String maintainer;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

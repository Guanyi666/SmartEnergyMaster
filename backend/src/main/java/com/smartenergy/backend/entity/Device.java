package com.smartenergy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Duan Guanyi
 * @version 1.0.0
 * @date 2026/3/18
 */
/**
 * 设备实体类
 */
@Data
@TableName("device")
public class Device {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String deviceCode;

    private String deviceName;

    private String deviceType;

    private String status;

    private LocalDateTime createdAt;

}

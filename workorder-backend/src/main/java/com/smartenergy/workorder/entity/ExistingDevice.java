package com.smartenergy.workorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 现有 device 表的只读映射（仅用于工单 JOIN 显示 deviceCode/deviceName）
 */
@Data
@TableName("device")
@Schema(description = "现有设备（只读）")
public class ExistingDevice {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("device_code")
    private String deviceCode;

    @TableField("device_name")
    private String deviceName;

    @TableField("device_type")
    private String deviceType;

    private String status;

    private String location;

    private String maintainer;

    private String description;
}

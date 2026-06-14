package com.smartenergy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device")
@Schema(description = "设备实体")
public class Device {

    @TableId(type = IdType.AUTO)
    @Schema(description = "设备 ID（自增主键）")
    private Integer id;

    @Schema(description = "设备编码（唯一）")
    private String deviceCode;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "设备类型（电弧炉/水泵/空压机）")
    private String deviceType;

    @Schema(description = "设备状态")
    private String status;

    @Schema(description = "安装位置")
    private String location;

    @Schema(description = "设备描述")
    private String description;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}

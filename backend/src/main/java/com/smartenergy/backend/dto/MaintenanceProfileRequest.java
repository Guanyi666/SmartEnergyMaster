package com.smartenergy.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class MaintenanceProfileRequest {

    @NotBlank(message = "维修人员档案姓名不能为空")
    private String name;

    @NotBlank(message = "维修人员技能等级不能为空")
    @Pattern(regexp = "^(JUNIOR|INTERMEDIATE|SENIOR|EXPERT)$",
            message = "维修人员技能等级不合法")
    private String skillLevel;

    private List<String> specializations;
    private String certification;
    private String phone;
    private String email;

    @NotNull(message = "最大工作负载不能为空")
    @Min(value = 1, message = "最大工作负载至少为 1")
    private Integer maxWorkload;
}

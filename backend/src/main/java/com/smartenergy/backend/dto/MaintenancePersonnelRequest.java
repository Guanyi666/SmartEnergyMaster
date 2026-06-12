package com.smartenergy.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "维修人员新增/编辑请求")
public class MaintenancePersonnelRequest {

    @NotBlank(message = "工号不能为空")
    @Pattern(regexp = "^E\\d{3,}$", message = "工号格式：E + 3 位以上数字，例如 E001")
    @Schema(description = "工号", example = "E001")
    private String employeeNo;

    @NotBlank(message = "姓名不能为空")
    @Schema(description = "姓名", example = "张工")
    private String name;

    @Schema(description = "联系电话", example = "13800000001")
    private String phone;

    @Schema(description = "邮箱", example = "zhang@example.com")
    private String email;

    @Schema(description = "头像底色", example = "#52c8ff")
    private String avatarColor;

    @Schema(description = "技能标签数组", example = "[\"电气\",\"自动化\"]")
    private List<String> specializations;

    @NotBlank(message = "技能等级不能为空")
    @Pattern(regexp = "^(JUNIOR|INTERMEDIATE|SENIOR|EXPERT)$",
             message = "技能等级必须为 JUNIOR/INTERMEDIATE/SENIOR/EXPERT 之一")
    @Schema(description = "技能等级", example = "EXPERT",
            allowableValues = {"JUNIOR","INTERMEDIATE","SENIOR","EXPERT"})
    private String skillLevel;

    @Schema(description = "证书描述", example = "高级工程师 / 15年")
    private String certification;

    @NotNull(message = "max_workload 不能为空")
    @Min(value = 1, message = "max_workload 至少为 1")
    @Schema(description = "最大并行处理数", example = "5")
    private Integer maxWorkload;
}

package com.smartenergy.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

@Data
@TableName(value = "workorder_maintenance_personnel", autoResultMap = false)
// ⚠️ 用 String 而非 List<String> 存 specializations
//    原因：MyBatis-Plus 的 JacksonTypeHandler 在 PostgreSQL JSONB 上不稳定，
//    JacksonTypeHandler 期望 String/Reader，PG 驱动返回 PGobject，会触发
//    ClassCastException 或 BadSqlGrammarException。
//    改用 String + 手动 JSON 序列化是最稳的方案，PG 接受将 String
//    隐式转 JSONB（只要内容是合法 JSON）。
//    autoResultMap=false 关闭 MyBatis-Plus 自动生成 resultMap，避免 TypeHandler 自动注入。
public class MaintenancePersonnel {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键 ID（自增）")
    private Long id;

    @Schema(description = "工号（唯一，登录用户名）", example = "E001")
    private String employeeNo;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像底色（hex）", example = "#52c8ff")
    private String avatarColor;

    /**
     * 技能标签 JSON 字符串（DB 列是 JSONB，PG 接受 String 写入）。
     * 应用层用 Jackson 解析/序列化（见 service 层的 toList/fromList 辅助）。
     */
    @TableField(jdbcType = JdbcType.VARCHAR)
    @Schema(description = "技能标签 JSON 字符串", hidden = true)
    private String specializations;

    @Schema(description = "技能等级：JUNIOR/INTERMEDIATE/SENIOR/EXPERT", example = "EXPERT")
    private String skillLevel;

    @Schema(description = "证书描述")
    private String certification;

    @Schema(description = "当前在处理工单数", example = "0")
    private Integer currentWorkload;

    @Schema(description = "最大并行处理数", example = "5")
    private Integer maxWorkload;

    @Schema(description = "是否在岗", example = "true")
    private Boolean isOnDuty;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}

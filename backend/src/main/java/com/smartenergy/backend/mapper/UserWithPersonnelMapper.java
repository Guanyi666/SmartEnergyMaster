package com.smartenergy.backend.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartenergy.backend.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * v6 改造：人员管理合并查询。
 * 不直接做 3 表 JOIN（实体字段不匹配），而是先用 BaseMapper 查 sys_user，
 * 然后用 IN 批量查 maintenance_personnel 和 workorder_maintenance_personnel，
 * 在 Service 层组装为 UserWithPersonnelVO。
 *
 * 之所以分两步：避免单个超宽 SQL，让 3 个实体各自维护独立性。
 */
@Mapper
public interface UserWithPersonnelMapper extends BaseMapper<SysUser> {

    /**
     * 批量查 sys_user 的 maintenance_personnel 档案（按 user_id IN）
     */
    @Select("""
        <script>
        SELECT user_id, name, phone, email,
               specializations, skill_level, certification
        FROM maintenance_personnel
        WHERE user_id IN
        <foreach collection='userIds' item='id' open='(' separator=',' close=')'>
            #{id}
        </foreach>
        </script>
        """)
    List<Map<String, Object>> selectArchivesByUserIds(@Param("userIds") List<Integer> userIds);

    /**
     * 批量查 sys_user 的 workorder_maintenance_personnel 排班（按 user_id IN）
     */
    @Select("""
        <script>
        SELECT id, user_id, avatar_color, current_workload, max_workload, is_on_duty
        FROM workorder_maintenance_personnel
        WHERE user_id IN
        <foreach collection='userIds' item='id' open='(' separator=',' close=')'>
            #{id}
        </foreach>
        </script>
        """)
    List<Map<String, Object>> selectSchedulesByUserIds(@Param("userIds") List<Integer> userIds);
}

package com.smartenergy.backend.mapper;

import com.smartenergy.backend.vo.WorkOrderAssignmentVO;
import com.smartenergy.backend.vo.WorkOrderReadVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工单 + 设备 + 指派人 JOIN 查询
 * （🟠 脏写风险修正：assigneeName 完全来自 workorder_assignment，不依赖 work_order.assignee 列）
 */
@Mapper
public interface WorkOrderQueryMapper {

    /**
     * 工单分页查询（带 device + 活跃指派人 JOIN）
     * @param status  PENDING/IN_PROGRESS/RESOLVED，null=不过滤
     * @param offset  偏移
     * @param limit   每页条数
     */
    @Select("""
        SELECT
            wo.id, wo.order_no, wo.device_id, wo.title, wo.fault_type, wo.description,
            wo.status, wo.priority, wo.assignee, wo.source,
            wo.source_time, wo.accepted_at, wo.resolved_at,
            wo.latest_temperature, wo.latest_vibration, wo.latest_pressure, wo.sop_id,
            wo.created_at, wo.updated_at,
            d.device_code, d.device_name, d.device_type, d.location AS device_location,
            a.personnel_id AS assignee_id, p.name AS assignee_name, a.assigned_at,
            COALESCE(
                (SELECT json_agg(json_build_object(
                    'id', a2.id,
                    'personnel_id', a2.personnel_id,
                    'name', p2.name,
                    'employee_no', p2.employee_no,
                    'avatar_color', p2.avatar_color,
                    'role', a2.role,
                    'assigned_at', a2.assigned_at
                ) ORDER BY a2.assigned_at ASC)::text
                FROM workorder_assignment a2
                LEFT JOIN workorder_maintenance_personnel p2 ON a2.personnel_id = p2.id
                WHERE a2.work_order_id = wo.id AND a2.released_at IS NULL),
                '[]'
            ) AS active_assignments_json,
            (SELECT COUNT(*)::int FROM workorder_assignment
             WHERE work_order_id = wo.id AND released_at IS NULL) AS assignee_count
        FROM work_order wo
        LEFT JOIN device d ON wo.device_id = d.id
        LEFT JOIN LATERAL (
            SELECT a2.id, a2.personnel_id, a2.assigned_at
            FROM workorder_assignment a2
            WHERE a2.work_order_id = wo.id AND a2.released_at IS NULL
            ORDER BY a2.assigned_at ASC
            LIMIT 1
        ) a ON TRUE
        LEFT JOIN workorder_maintenance_personnel p ON a.personnel_id = p.id
        WHERE (#{status}::text IS NULL OR wo.status = #{status})
        ORDER BY wo.created_at DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<WorkOrderReadVO> selectOrderList(@Param("status") String status,
                                          @Param("offset") long offset,
                                          @Param("limit") long limit);

    @Select("""
        SELECT COUNT(*)
        FROM work_order wo
        WHERE (#{status}::text IS NULL OR wo.status = #{status})
        """)
    long countOrderList(@Param("status") String status);

    /**
     * 工单详情（同样 JOIN，含 active_assignments_json 全量）
     */
    @Select("""
        SELECT
            wo.id, wo.order_no, wo.device_id, wo.title, wo.fault_type, wo.description,
            wo.status, wo.priority, wo.assignee, wo.source,
            wo.source_time, wo.accepted_at, wo.resolved_at,
            wo.latest_temperature, wo.latest_vibration, wo.latest_pressure, wo.sop_id,
            wo.created_at, wo.updated_at,
            d.device_code, d.device_name, d.device_type, d.location AS device_location,
            a.personnel_id AS assignee_id, p.name AS assignee_name, a.assigned_at,
            COALESCE(
                (SELECT json_agg(json_build_object(
                    'id', a2.id,
                    'personnel_id', a2.personnel_id,
                    'name', p2.name,
                    'employee_no', p2.employee_no,
                    'avatar_color', p2.avatar_color,
                    'role', a2.role,
                    'assigned_at', a2.assigned_at
                ) ORDER BY a2.assigned_at ASC)::text
                FROM workorder_assignment a2
                LEFT JOIN workorder_maintenance_personnel p2 ON a2.personnel_id = p2.id
                WHERE a2.work_order_id = wo.id AND a2.released_at IS NULL),
                '[]'
            ) AS active_assignments_json,
            (SELECT COUNT(*)::int FROM workorder_assignment
             WHERE work_order_id = wo.id AND released_at IS NULL) AS assignee_count
        FROM work_order wo
        LEFT JOIN device d ON wo.device_id = d.id
        LEFT JOIN LATERAL (
            SELECT a2.id, a2.personnel_id, a2.assigned_at
            FROM workorder_assignment a2
            WHERE a2.work_order_id = wo.id AND a2.released_at IS NULL
            ORDER BY a2.assigned_at ASC
            LIMIT 1
        ) a ON TRUE
        LEFT JOIN workorder_maintenance_personnel p ON a.personnel_id = p.id
        WHERE wo.id = #{id}
        """)
    WorkOrderReadVO selectOrderById(@Param("id") Long id);

    /**
     * 工单指派历史（含人员名 + 头像）
     */
    @Select("""
        SELECT
            a.id, a.work_order_id, a.personnel_id, a.role,
            a.assigned_at, a.released_at, a.note,
            p.name AS personnel_name, p.employee_no, p.avatar_color
        FROM workorder_assignment a
        JOIN workorder_maintenance_personnel p ON a.personnel_id = p.id
        WHERE a.work_order_id = #{workOrderId}
        ORDER BY a.assigned_at DESC
        """)
    List<WorkOrderAssignmentVO> selectAssignmentsByWorkOrder(@Param("workOrderId") Long workOrderId);
}

package com.smartenergy.backend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartenergy.backend.vo.WorkOrderAssignmentVO;
import com.smartenergy.backend.vo.WorkOrderReadVO;

import java.util.List;

public interface WorkOrderReadService {

    /** 分页查询工单（JOIN 设备 + 活跃指派人） */
    Page<WorkOrderReadVO> listOrders(String status, long pageNum, long pageSize);

    /** 详情（JOIN 设备 + 活跃指派人） */
    WorkOrderReadVO getOrderDetail(Long id);

    /** 工单指派历史 */
    List<WorkOrderAssignmentVO> getAssignments(Long workOrderId);
}

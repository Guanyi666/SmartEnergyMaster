package com.smartenergy.backend.service;

import com.smartenergy.backend.dto.SparePartCreateRequest;
import com.smartenergy.backend.dto.SparePartUsageRequest;
import com.smartenergy.backend.entity.WorkOrder;
import com.smartenergy.backend.vo.SparePartUsageVO;
import com.smartenergy.backend.vo.SparePartVO;

import java.util.List;

public interface SparePartService {

    List<SparePartVO> listParts(String keyword, Boolean lowStockOnly);

    SparePartVO getPart(Long id);

    SparePartVO createPart(SparePartCreateRequest request);

    SparePartVO updatePart(Long id, SparePartCreateRequest request);

    void deletePart(Long id);

    /**
     * 备件领用：扣减库存 + 写入领用记录
     */
    SparePartUsageVO recordUsage(SparePartUsageRequest request);

    List<SparePartUsageVO> recordUsages(List<SparePartUsageRequest> requests);

    /**
     * 某备件的领用历史（最新在前）
     */
    List<SparePartUsageVO> listUsages(Long partId, String userName, int limit);

    /**
     * 某工单关联的领用记录
     */
    List<SparePartUsageVO> listUsagesByWorkOrder(Long workOrderId);
    /**
     * 工单闭环时自动扣减备件库存:根据工单关联 SOP 的 requiredParts 列表,
     * 为每个尚未手工登记过领用的备件扣减 1 件库存并写 SparePartUsage 记录。
     * 备件不存在/库存为 0 的会跳过并加入异常清单,异常清单追加到工单描述。
     */
    List<SparePartUsageVO> autoDeductForWorkOrder(WorkOrder workOrder);
}

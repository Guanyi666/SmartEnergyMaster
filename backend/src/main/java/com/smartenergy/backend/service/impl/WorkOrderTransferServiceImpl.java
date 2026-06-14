package com.smartenergy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartenergy.backend.dto.TransferRequestCreateRequest;
import com.smartenergy.backend.dto.TransferRequestReviewRequest;
import com.smartenergy.backend.dto.WorkOrderReplaceRequest;
import com.smartenergy.backend.entity.Device;
import com.smartenergy.backend.entity.MaintenancePersonnel;
import com.smartenergy.backend.entity.MaintenancePersonnelArchive;
import com.smartenergy.backend.entity.SysUser;
import com.smartenergy.backend.entity.WorkOrder;
import com.smartenergy.backend.entity.WorkOrderAssignment;
import com.smartenergy.backend.entity.WorkOrderTransferRequest;
import com.smartenergy.backend.mapper.DeviceMapper;
import com.smartenergy.backend.mapper.MaintenancePersonnelArchiveMapper;
import com.smartenergy.backend.mapper.MaintenancePersonnelMapper;
import com.smartenergy.backend.mapper.WorkOrderAssignmentMapper;
import com.smartenergy.backend.mapper.WorkOrderMapper;
import com.smartenergy.backend.mapper.SysUserMapper;
import com.smartenergy.backend.mapper.WorkOrderTransferRequestMapper;
import com.smartenergy.backend.service.WorkOrderAssignmentService;
import com.smartenergy.backend.service.WorkOrderTransferService;
import com.smartenergy.backend.vo.WorkOrderTransferRequestVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkOrderTransferServiceImpl implements WorkOrderTransferService {

    private final WorkOrderTransferRequestMapper transferMapper;
    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderAssignmentMapper assignmentMapper;
    private final MaintenancePersonnelMapper personnelMapper;
    private final MaintenancePersonnelArchiveMapper archiveMapper;
    private final DeviceMapper deviceMapper;
    private final SysUserMapper sysUserMapper;
    private final WorkOrderAssignmentService assignmentService;

    @Override
    @Transactional
    public WorkOrderTransferRequestVO create(Long workOrderId, TransferRequestCreateRequest request) {
        WorkOrder workOrder = requireWorkOrder(workOrderId);
        if ("RESOLVED".equals(workOrder.getStatus())) {
            throw new IllegalStateException("已完成工单不能申请转派");
        }

        MaintenancePersonnel requester = requireCurrentPersonnel();
        Long activeCount = assignmentMapper.selectCount(new QueryWrapper<WorkOrderAssignment>()
                .eq("work_order_id", workOrderId)
                .eq("personnel_id", requester.getId())
                .isNull("released_at"));
        if (activeCount == null || activeCount == 0) {
            throw new IllegalStateException("只有当前工单的维修人员可以申请转派");
        }

        Long pendingCount = transferMapper.selectCount(new QueryWrapper<WorkOrderTransferRequest>()
                .eq("work_order_id", workOrderId)
                .eq("requester_personnel_id", requester.getId())
                .eq("status", "PENDING"));
        if (pendingCount != null && pendingCount > 0) {
            throw new IllegalStateException("该工单已有待审批的转派申请");
        }

        WorkOrderTransferRequest entity = new WorkOrderTransferRequest();
        entity.setWorkOrderId(workOrderId);
        entity.setRequesterPersonnelId(requester.getId());
        entity.setReason(request.getReason().trim());
        entity.setStatus("PENDING");
        entity.setRequestedAt(LocalDateTime.now());
        transferMapper.insert(entity);
        return toVO(entity);
    }

    @Override
    public List<WorkOrderTransferRequestVO> list(String status, boolean mine) {
        Authentication authentication = currentAuthentication();
        boolean engineer = authentication.getAuthorities().stream()
                .anyMatch(authority -> "MAINTENANCE_ENGINEER".equals(authority.getAuthority()));

        QueryWrapper<WorkOrderTransferRequest> wrapper = new QueryWrapper<WorkOrderTransferRequest>()
                .orderByDesc("requested_at");
        if (StringUtils.hasText(status)) {
            wrapper.eq("status", status.toUpperCase());
        }
        if (mine || engineer) {
            wrapper.eq("requester_personnel_id", requireCurrentPersonnel().getId());
        }
        return transferMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    @Transactional
    public WorkOrderTransferRequestVO review(Long id, TransferRequestReviewRequest request) {
        WorkOrderTransferRequest entity = transferMapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("转派申请不存在");
        }
        if (!"PENDING".equals(entity.getStatus())) {
            throw new IllegalStateException("该转派申请已完成审批");
        }

        if (Boolean.TRUE.equals(request.getApproved())) {
            if (request.getNewPersonnelId() == null) {
                throw new IllegalArgumentException("接受转派申请时必须重新指派人员");
            }
            if (request.getNewPersonnelId().equals(entity.getRequesterPersonnelId())) {
                throw new IllegalArgumentException("新指派人员不能是转派申请发起者");
            }
            WorkOrderReplaceRequest replaceRequest = new WorkOrderReplaceRequest();
            replaceRequest.setNewPersonnelId(request.getNewPersonnelId());
            replaceRequest.setNote("转派审批通过" + (StringUtils.hasText(request.getReviewNote())
                    ? "：" + request.getReviewNote().trim() : ""));
            assignmentService.replace(entity.getWorkOrderId(), entity.getRequesterPersonnelId(), replaceRequest);
            entity.setStatus("APPROVED");
            entity.setNewPersonnelId(request.getNewPersonnelId());
        } else {
            entity.setStatus("REJECTED");
        }
        entity.setReviewerUsername(currentAuthentication().getName());
        entity.setReviewNote(request.getReviewNote());
        entity.setReviewedAt(LocalDateTime.now());
        transferMapper.updateById(entity);
        return toVO(entity);
    }

    private WorkOrder requireWorkOrder(Long id) {
        WorkOrder workOrder = workOrderMapper.selectById(id);
        if (workOrder == null) {
            throw new IllegalArgumentException("工单不存在");
        }
        return workOrder;
    }

    private MaintenancePersonnel requireCurrentPersonnel() {
        String username = currentAuthentication().getName();
        SysUser sysUser = sysUserMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username));
        MaintenancePersonnel personnel = sysUser == null ? null : personnelMapper.selectOne(
                new QueryWrapper<MaintenancePersonnel>().eq("user_id", sysUser.getId()));
        if (personnel == null) {
            throw new IllegalStateException("当前账号未关联维修人员档案");
        }
        return personnel;
    }

    private Authentication currentAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("请先登录");
        }
        return authentication;
    }

    private WorkOrderTransferRequestVO toVO(WorkOrderTransferRequest entity) {
        WorkOrderTransferRequestVO vo = new WorkOrderTransferRequestVO();
        BeanUtils.copyProperties(entity, vo);

        WorkOrder workOrder = workOrderMapper.selectById(entity.getWorkOrderId());
        if (workOrder != null) {
            vo.setWorkOrderNo(workOrder.getOrderNo());
            vo.setWorkOrderTitle(workOrder.getTitle());
            Device device = deviceMapper.selectById(workOrder.getDeviceId());
            if (device != null) {
                vo.setDeviceName(device.getDeviceName());
            }
        }
        MaintenancePersonnel requester = personnelMapper.selectById(entity.getRequesterPersonnelId());
        if (requester != null) {
            vo.setRequesterEmployeeNo(null);
            vo.setRequesterName(nameOf(requester));
        }
        if (entity.getNewPersonnelId() != null) {
            MaintenancePersonnel newPersonnel = personnelMapper.selectById(entity.getNewPersonnelId());
            if (newPersonnel != null) {
                vo.setNewPersonnelName(nameOf(newPersonnel));
            }
        }
        return vo;
    }

    /** v4: 从 archive 查人员姓名（personnel.getName() 已删） */
    private String nameOf(MaintenancePersonnel p) {
        if (p == null) return null;
        MaintenancePersonnelArchive archive = archiveMapper.selectOne(
                new QueryWrapper<MaintenancePersonnelArchive>().eq("user_id", p.getUserId()));
        return archive == null ? null : archive.getName();
    }
}

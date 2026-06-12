package com.smartenergy.backend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartenergy.backend.dto.MaintenancePersonnelRequest;
import com.smartenergy.backend.dto.PageQuery;
import com.smartenergy.backend.entity.MaintenancePersonnel;
import com.smartenergy.backend.vo.MaintenancePersonnelVO;

import java.util.List;

public interface MaintenancePersonnelService {

    /**
     * 分页 + 筛选 查询
     * @param pageQuery    分页参数
     * @param specialization 技能过滤（contains 匹配特殊化数组 JSONB）
     * @param skillLevel   技能等级过滤
     * @param onDuty       在岗过滤（true / false / null=不过滤）
     */
    Page<MaintenancePersonnelVO> list(PageQuery pageQuery, String specialization,
                                      String skillLevel, Boolean onDuty);

    /** 详情 */
    MaintenancePersonnelVO getById(Long id);

    /** 新增 */
    MaintenancePersonnelVO create(MaintenancePersonnelRequest req);

    /** 编辑 */
    MaintenancePersonnelVO update(Long id, MaintenancePersonnelRequest req);

    /**
     * 删除
     * @throws IllegalStateException 当前负载 != 0 时
     */
    void delete(Long id);

    /** 切岗（true 在岗 / false 离岗） */
    MaintenancePersonnelVO toggleDuty(Long id, boolean onDuty);

    /** 给 Service 间调用：从 entity 转 VO（含 workload rate 计算） */
    MaintenancePersonnelVO toVO(MaintenancePersonnel entity);
}

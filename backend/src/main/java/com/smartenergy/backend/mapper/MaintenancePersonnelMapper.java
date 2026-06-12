package com.smartenergy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartenergy.backend.entity.MaintenancePersonnel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MaintenancePersonnelMapper extends BaseMapper<MaintenancePersonnel> {

    /**
     * 原子加减 current_workload（GREATEST 兜底防止负值）
     * 替代业务层 read-modify-write，杜绝并发 assign 丢 increment
     */
    @Update("UPDATE workorder_maintenance_personnel " +
            "SET current_workload = GREATEST(0, current_workload + #{delta}), updated_at = NOW() " +
            "WHERE id = #{personnelId}")
    int bumpWorkloadAtomic(@Param("personnelId") Long personnelId, @Param("delta") int delta);
}

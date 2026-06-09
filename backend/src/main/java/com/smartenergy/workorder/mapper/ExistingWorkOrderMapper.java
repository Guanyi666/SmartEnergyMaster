package com.smartenergy.workorder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartenergy.workorder.entity.ExistingWorkOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExistingWorkOrderMapper extends BaseMapper<ExistingWorkOrder> {
}

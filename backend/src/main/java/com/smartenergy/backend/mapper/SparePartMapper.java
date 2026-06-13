package com.smartenergy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartenergy.backend.entity.SparePart;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface SparePartMapper extends BaseMapper<SparePart> {

    @Update("""
            UPDATE spare_part
            SET quantity = quantity - #{quantity}, updated_at = #{updatedAt}
            WHERE id = #{id} AND quantity >= #{quantity}
            """)
    int deductStock(@Param("id") Long id,
                    @Param("quantity") Integer quantity,
                    @Param("updatedAt") LocalDateTime updatedAt);
}

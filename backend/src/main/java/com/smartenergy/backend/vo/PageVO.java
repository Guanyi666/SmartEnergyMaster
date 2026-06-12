package com.smartenergy.backend.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

@Data
public class PageVO<T> {

    private long total;
    private int page;
    private int size;
    private List<T> records;

    public static <T> PageVO<T> of(IPage<T> page) {
        PageVO<T> vo = new PageVO<>();
        vo.setTotal(page.getTotal());
        vo.setPage((int) page.getCurrent());
        vo.setSize((int) page.getSize());
        vo.setRecords(page.getRecords());
        return vo;
    }
}

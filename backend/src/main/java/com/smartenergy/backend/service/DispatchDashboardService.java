package com.smartenergy.backend.service;

import com.smartenergy.backend.vo.DispatchBoardVO;
import com.smartenergy.backend.vo.DispatchSummaryVO;

public interface DispatchDashboardService {

    /** 调度总览 */
    DispatchSummaryVO summary();

    /** 调度看板（按技能分组） */
    DispatchBoardVO board();
}

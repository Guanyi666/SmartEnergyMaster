package com.smartenergy.workorder.service;

import com.smartenergy.workorder.vo.DispatchBoardVO;
import com.smartenergy.workorder.vo.DispatchSummaryVO;

public interface DispatchDashboardService {

    /** 调度总览 */
    DispatchSummaryVO summary();

    /** 调度看板（按技能分组） */
    DispatchBoardVO board();
}

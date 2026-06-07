package com.smartenergy.backend.service;

import com.smartenergy.backend.vo.DashboardSummaryVO;
import com.smartenergy.backend.vo.DispatchAdviceVO;

public interface DashboardService {

    DashboardSummaryVO getSummary(String deviceCode);

    DispatchAdviceVO getDispatchAdvice(String deviceCode);
}

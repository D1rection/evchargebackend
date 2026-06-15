package bupt.evchargebackend.service.admin;

import bupt.evchargebackend.common.response.PageResult;
import bupt.evchargebackend.common.response.Result;

import java.util.Map;

public interface AdminMonitorService {
    Result<PageResult<Map<String, Object>>> listPileStatus(Integer pageNum, Integer pageSize);
    Result<Map<String, Object>> getDashboard();
}

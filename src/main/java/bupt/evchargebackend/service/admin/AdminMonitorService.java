package bupt.evchargebackend.service.admin;

import bupt.evchargebackend.common.response.PageResult;

import java.util.Map;

public interface AdminMonitorService {
    PageResult<Map<String, Object>> listPileStatus(Integer pageNum, Integer pageSize);
    Map<String, Object> getDashboard();
}

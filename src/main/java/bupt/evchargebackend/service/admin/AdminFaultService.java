package bupt.evchargebackend.service.admin;

import bupt.evchargebackend.common.response.PageResult;

import java.util.Map;

public interface AdminFaultService {
    PageResult<Map<String, Object>> listFaults(int pageNum, int pageSize, Integer status);
    void resolveFault(String faultId, Integer resolveCode, String remark);
}

package bupt.evchargebackend.service.admin;

import bupt.evchargebackend.common.response.PageResult;
import bupt.evchargebackend.common.response.Result;

import java.util.Map;

public interface AdminFaultService {
    Result<PageResult<Map<String, Object>>> listFaults(int pageNum, int pageSize, Integer status);
    Result<Void> resolveFault(String faultId, Integer resolveCode, String remark);
}

package bupt.evchargebackend.service.admin;

import bupt.evchargebackend.common.response.Result;

import java.util.Map;

public interface AdminAccountService {
    Result<Map<String, Object>> register(String userName, String password);
    Result<Map<String, Object>> login(String userName, String password);
}

package bupt.evchargebackend.service.admin;

import java.util.Map;

public interface AdminAccountService {
    Map<String, Object> register(String userName, String password);
    Map<String, Object> login(String userName, String password);
}

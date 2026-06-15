package bupt.evchargebackend.service.account;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface AccountService {

    Map<String, Object> createAccount(String username, String password, String carId, String carNo,
                                      BigDecimal batteryCapacityKwh, String role);

    Map<String, Object> login(String username, String password);

    Map<String, Object> addVehicle(String userId, String username, String carId, String carNo,
                                   BigDecimal batteryCapacityKwh);

    List<Map<String, Object>> listVehicles(String userId, String username);

    Map<String, Object> verifyToken(String token);
}

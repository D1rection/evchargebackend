package bupt.evchargebackend.service.account;

import java.math.BigDecimal;
import java.util.Map;

public interface AccountService {

    Map<String, Object> createAccount(String username, String password, String carId, String carNo,
                                      BigDecimal batteryCapacityKwh, String role);

    Map<String, Object> login(String username, String password);

    Map<String, Object> addVehicle(String userId, String carId, String carNo, BigDecimal batteryCapacityKwh);

    Map<String, Object> verifyToken(String token);
}

package bupt.evchargebackend.controller.account;

import bupt.evchargebackend.common.exception.BusinessException;
import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.service.account.AccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/api/v1/account/create")
    public Result<Map<String, Object>> createAccount(@RequestBody Map<String, Object> request) {
        return Result.success(accountService.createAccount(
                pickString(request, "username", "userName"),
                pickString(request, "password"),
                pickString(request, "carId", "car_Id", "car_id"),
                pickString(request, "carNo", "car_No", "car_no"),
                pickBigDecimal(request, "batteryCapacityKwh", "carCapacity", "car_Capacity", "car_capacity"),
                pickString(request, "role")
        ));
    }

    @PostMapping("/api/v1/account/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, Object> request) {
        return Result.success(accountService.login(
                pickString(request, "username", "userName"),
                pickString(request, "password")
        ));
    }

    @PostMapping("/api/v1/account/vehicles")
    public Result<Map<String, Object>> addVehicle(@RequestBody Map<String, Object> request) {
        return Result.success(accountService.addVehicle(
                pickString(request, "userId", "user_Id", "user_id"),
                pickString(request, "carId", "car_Id", "car_id"),
                pickString(request, "carNo", "car_No", "car_no"),
                pickBigDecimal(request, "batteryCapacityKwh", "carCapacity", "car_Capacity", "car_capacity")
        ));
    }

    @GetMapping("/api/v1/auth/verify")
    public Result<Map<String, Object>> verify(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return Result.success(accountService.verifyToken(resolveBearerToken(authorization)));
    }

    private String resolveBearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new BusinessException(401, "Authorization header is required");
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }

    private String pickString(Map<String, Object> request, String... keys) {
        for (String key : keys) {
            Object value = request.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private BigDecimal pickBigDecimal(Map<String, Object> request, String... keys) {
        String value = pickString(request, keys);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new BusinessException("number field is invalid");
        }
    }
}

package bupt.evchargebackend.service.account.impl;

import bupt.evchargebackend.common.exception.BusinessException;
import bupt.evchargebackend.common.exception.ErrorCode;
import bupt.evchargebackend.common.jwt.JwtUtil;
import bupt.evchargebackend.entity.user.Car;
import bupt.evchargebackend.entity.user.UserAccount;
import bupt.evchargebackend.entity.user.enums.UserRole;
import bupt.evchargebackend.mapper.user.CarMapper;
import bupt.evchargebackend.mapper.user.UserAccountMapper;
import bupt.evchargebackend.service.account.AccountService;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountServiceImpl implements AccountService {

    private final UserAccountMapper userAccountMapper;
    private final CarMapper carMapper;
    private final JwtUtil jwtUtil;

    public AccountServiceImpl(UserAccountMapper userAccountMapper, CarMapper carMapper, JwtUtil jwtUtil) {
        this.userAccountMapper = userAccountMapper;
        this.carMapper = carMapper;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Map<String, Object> createAccount(String username, String password, String carId, String carNo,
                                             BigDecimal batteryCapacityKwh, String role) {
        requireText(username, "username is required");
        requireText(password, "password is required");
        requirePositive(batteryCapacityKwh, "batteryCapacityKwh must be greater than 0");

        UserAccount existing = userAccountMapper.selectOne(
                new QueryWrapper<UserAccount>().eq("username", username).last("LIMIT 1")
        );
        if (existing != null) {
            throw new BusinessException(409, "username already exists");
        }

        UserRole userRole = parseRole(role);
        String userId = UUID.randomUUID().toString();
        String finalCarId = hasText(carId) ? carId : UUID.randomUUID().toString();
        String finalCarNo = hasText(carNo) ? carNo : finalCarId;

        UserAccount account = new UserAccount();
        account.setUserId(userId);
        account.setUsername(username);
        account.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        account.setRole(userRole);
        userAccountMapper.insert(account);

        Car car = new Car();
        car.setCarId(finalCarId);
        car.setUserId(userId);
        car.setCarNo(finalCarNo);
        car.setBatteryCapacityKwh(batteryCapacityKwh);
        carMapper.insert(car);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("carId", finalCarId);
        data.put("userName", username);
        return data;
    }

    @Override
    public Map<String, Object> login(String username, String password) {
        requireText(username, "username is required");
        requireText(password, "password is required");

        UserAccount account = userAccountMapper.selectOne(
                new QueryWrapper<UserAccount>().eq("username", username).last("LIMIT 1")
        );
        if (account == null || !BCrypt.checkpw(password, account.getPasswordHash())) {
            throw new BusinessException(401, "username or password is incorrect");
        }

        String role = account.getRole().name();
        String token = jwtUtil.generate(account.getUserId(), role);

        Map<String, Object> data = buildUserData(account);
        data.put("token", token);
        return data;
    }

    @Override
    public Map<String, Object> addVehicle(String userId, String username, String carId, String carNo,
                                          BigDecimal batteryCapacityKwh) {
        requirePositive(batteryCapacityKwh, "batteryCapacityKwh must be greater than 0");

        UserAccount account = findAccount(userId, username);
        if (account == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        String finalCarId = hasText(carId) ? carId : UUID.randomUUID().toString();
        String finalCarNo = hasText(carNo) ? carNo : finalCarId;

        if (carMapper.selectById(finalCarId) != null) {
            throw new BusinessException(409, "carId already exists");
        }

        Car car = new Car();
        car.setCarId(finalCarId);
        car.setUserId(account.getUserId());
        car.setCarNo(finalCarNo);
        car.setBatteryCapacityKwh(batteryCapacityKwh);
        carMapper.insert(car);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("carId", finalCarId);
        data.put("carNo", finalCarNo);
        data.put("carCapacity", batteryCapacityKwh);
        return data;
    }

    @Override
    public List<Map<String, Object>> listVehicles(String userId, String username) {
        UserAccount account = findAccount(userId, username);
        if (account == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return listVehicleData(account.getUserId());
    }

    @Override
    public Map<String, Object> verifyToken(String token) {
        requireText(token, "token is required");
        try {
            String userId = jwtUtil.parseUserId(token);
            UserAccount account = userAccountMapper.selectById(userId);
            if (account == null) {
                throw new BusinessException(401, "token is invalid or expired");
            }

            return buildUserData(account);
        } catch (Exception e) {
            throw new BusinessException(401, "token is invalid or expired");
        }
    }

    private UserRole parseRole(String value) {
        if (!hasText(value)) {
            return UserRole.USER;
        }
        try {
            return UserRole.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("role must be USER or ADMIN");
        }
    }

    private void requireText(String value, String message) {
        if (!hasText(value)) {
            throw new BusinessException(message);
        }
    }

    private void requirePositive(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Map<String, Object> buildUserData(UserAccount account) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userName", account.getUsername());
        data.put("vehicles", listVehicleData(account.getUserId()));
        return data;
    }

    private UserAccount findAccount(String userId, String username) {
        if (hasText(userId)) {
            return userAccountMapper.selectById(userId);
        }
        if (hasText(username)) {
            return userAccountMapper.selectOne(
                    new QueryWrapper<UserAccount>().eq("username", username).last("LIMIT 1")
            );
        }
        throw new BusinessException("userId or userName is required");
    }

    private List<Map<String, Object>> listVehicleData(String userId) {
        return carMapper.selectList(new QueryWrapper<Car>().eq("user_id", userId))
                .stream()
                .map(this::buildVehicleData)
                .toList();
    }

    private Map<String, Object> buildVehicleData(Car car) {
        Map<String, Object> vehicle = new LinkedHashMap<>();
        vehicle.put("carId", car.getCarId());
        vehicle.put("carNo", car.getCarNo());
        vehicle.put("carCapacity", car.getBatteryCapacityKwh());
        return vehicle;
    }
}

package bupt.evchargebackend.service.admin.impl;

import bupt.evchargebackend.common.exception.BusinessException;
import bupt.evchargebackend.common.jwt.JwtUtil;
import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.entity.user.UserAccount;
import bupt.evchargebackend.entity.user.enums.UserRole;
import bupt.evchargebackend.mapper.user.UserAccountMapper;
import bupt.evchargebackend.service.admin.AdminAccountService;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AdminAccountServiceImpl implements AdminAccountService {

    private final UserAccountMapper userAccountMapper;
    private final JwtUtil jwtUtil;

    public AdminAccountServiceImpl(UserAccountMapper userAccountMapper, JwtUtil jwtUtil) {
        this.userAccountMapper = userAccountMapper;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Result<Map<String, Object>> register(String userName, String password) {
        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAccount::getUsername, userName);
        if (userAccountMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(409, "用户名已存在");
        }

        UserAccount account = new UserAccount();
        account.setUserId("U" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        account.setUsername(userName);
        account.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        account.setRole(UserRole.ADMIN);
        userAccountMapper.insert(account);

        return Result.success(Map.of("userId", account.getUserId(),
                      "userName", account.getUsername(),
                      "role", "ADMIN"));
    }

    @Override
    public Result<Map<String, Object>> login(String userName, String password) {
        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAccount::getUsername, userName);
        UserAccount account = userAccountMapper.selectOne(wrapper);

        if (account == null || !BCrypt.checkpw(password, account.getPasswordHash())
                || account.getRole() != UserRole.ADMIN) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        String token = jwtUtil.generate(account.getUserId(), account.getRole().name());
        return Result.success(Map.of("userName", account.getUsername(), "token", token));
    }
}

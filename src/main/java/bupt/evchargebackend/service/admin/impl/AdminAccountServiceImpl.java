package bupt.evchargebackend.service.admin.impl;

import bupt.evchargebackend.common.exception.BusinessException;
import bupt.evchargebackend.common.exception.ErrorCode;
import bupt.evchargebackend.common.jwt.JwtUtil;
import bupt.evchargebackend.entity.user.UserAccount;
import bupt.evchargebackend.entity.user.enums.UserRole;
import bupt.evchargebackend.mapper.user.UserAccountMapper;
import bupt.evchargebackend.service.admin.AdminAccountService;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * 管理员账号服务实现。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@Service
public class AdminAccountServiceImpl implements AdminAccountService {

    private final UserAccountMapper userAccountMapper;
    private final JwtUtil jwtUtil;

    public AdminAccountServiceImpl(UserAccountMapper userAccountMapper, JwtUtil jwtUtil) {
        this.userAccountMapper = userAccountMapper;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Map<String, Object> register(String userName, String password) {
        // 检查用户名唯一性
        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAccount::getUsername, userName);
        if (userAccountMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(409, "用户名已存在");
        }

        // 创建管理员账号
        UserAccount account = new UserAccount();
        account.setUserId("U" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        account.setUsername(userName);
        account.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        account.setRole(UserRole.ADMIN);
        userAccountMapper.insert(account);

        return Map.of("userId", account.getUserId(),
                      "userName", account.getUsername(),
                      "role", "ADMIN");
    }

    @Override
    public Map<String, Object> login(String userName, String password) {
        // 查找用户
        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAccount::getUsername, userName);
        UserAccount account = userAccountMapper.selectOne(wrapper);

        if (account == null) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 验证密码
        if (!BCrypt.checkpw(password, account.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 检查角色
        if (account.getRole() != UserRole.ADMIN) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 生成 JWT
        String token = jwtUtil.generate(account.getUserId(), account.getRole().name());

        return Map.of("userName", account.getUsername(), "token", token);
    }
}

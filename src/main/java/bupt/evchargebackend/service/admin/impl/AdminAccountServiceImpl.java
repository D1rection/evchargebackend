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
 * <p>
 * 使用 MyBatis Plus 操作 {@link UserAccount} 实体，
 * 密码通过 BCrypt 哈希存储，登录成功后返回 JWT Token。
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

    /**
     * 管理员注册。
     * <p>
     * 校验用户名唯一性后创建管理员账号，密码使用 BCrypt 加密存储。
     *
     * @param userName 用户名
     * @param password 密码（明文）
     * @return {@code {userId, userName, role}} 注册成功的用户信息
     * @throws BusinessException 用户名已存在时抛出（409）
     */
    @Override
    public Map<String, Object> register(String userName, String password) {
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

        return Map.of("userId", account.getUserId(),
                      "userName", account.getUsername(),
                      "role", "ADMIN");
    }

    /**
     * 管理员登录。
     * <p>
     * 校验用户名、密码和角色后生成 JWT Token。
     *
     * @param userName 用户名
     * @param password 密码（明文）
     * @return {@code {userName, token}} 登录成功后的用户名和 JWT Token
     * @throws BusinessException 用户名或密码错误，或角色非 ADMIN 时抛出（401）
     */
    @Override
    public Map<String, Object> login(String userName, String password) {
        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAccount::getUsername, userName);
        UserAccount account = userAccountMapper.selectOne(wrapper);

        if (account == null) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        if (!BCrypt.checkpw(password, account.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        if (account.getRole() != UserRole.ADMIN) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        String token = jwtUtil.generate(account.getUserId(), account.getRole().name());

        return Map.of("userName", account.getUsername(), "token", token);
    }
}

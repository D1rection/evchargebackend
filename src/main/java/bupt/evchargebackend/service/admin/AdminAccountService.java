package bupt.evchargebackend.service.admin;

import bupt.evchargebackend.common.response.Result;

import java.util.Map;

/**
 * 管理员账号服务。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
public interface AdminAccountService {

    /**
     * 管理员注册。
     *
     * @param userName 用户名
     * @param password 密码（明文）
     * @return 成功时 {@code {userId, userName, role}}；用户名已存在时 {@code code=409}
     */
    Result<Map<String, Object>> register(String userName, String password);

    /**
     * 管理员登录。
     *
     * @param userName 用户名
     * @param password 密码（明文）
     * @return 成功时 {@code {userName, token}}；认证失败时 {@code code=401}
     */
    Result<Map<String, Object>> login(String userName, String password);
}

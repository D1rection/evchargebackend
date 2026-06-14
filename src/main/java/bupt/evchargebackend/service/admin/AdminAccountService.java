package bupt.evchargebackend.service.admin;

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
     * @return {userId, userName, role}
     */
    Map<String, Object> register(String userName, String password);

    /**
     * 管理员登录。
     *
     * @param userName 用户名
     * @param password 密码（明文）
     * @return {userName, token}
     */
    Map<String, Object> login(String userName, String password);
}

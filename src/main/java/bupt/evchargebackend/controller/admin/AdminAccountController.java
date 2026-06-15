package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.AdminAccountRequest;
import bupt.evchargebackend.service.admin.AdminAccountService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员账号 Controller。
 * <p>
 * 提供管理员注册和登录接口。
 * 注册成功后返回用户基本信息，登录成功后返回 JWT Token。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@RestController
@RequestMapping("/admin/account")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    public AdminAccountController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    /**
     * 管理员注册。
     *
     * @param request 注册请求体，包含 {@code userName} 和 {@code password}
     * @return {@code {userId, userName, role}} 注册成功的用户信息
     */
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody AdminAccountRequest request) {
        Map<String, Object> result = adminAccountService.register(
                request.getUserName(), request.getPassword());
        return Result.success(result);
    }

    /**
     * 管理员登录。
     *
     * @param request 登录请求体，包含 {@code userName} 和 {@code password}
     * @return {@code {userName, token}} 登录成功的用户名和 JWT Token
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody AdminAccountRequest request) {
        Map<String, Object> result = adminAccountService.login(
                request.getUserName(), request.getPassword());
        return Result.success(result);
    }
}

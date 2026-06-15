package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.AdminAccountRequest;
import bupt.evchargebackend.service.admin.AdminAccountService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员账号 Controller。
 * <p>
 * 提供管理员注册和登录接口，使用 {@link Result#of(java.util.function.Supplier)}
 * 显式处理业务异常的错误码。
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
     * @return 成功时 {@code {userId, userName, role}}；用户名已存在时 {@code code=409}
     */
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody AdminAccountRequest request) {
        return Result.of(() -> adminAccountService.register(
                request.getUserName(), request.getPassword()));
    }

    /**
     * 管理员登录。
     *
     * @param request 登录请求体，包含 {@code userName} 和 {@code password}
     * @return 成功时 {@code {userName, token}}；认证失败时 {@code code=401}
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody AdminAccountRequest request) {
        return Result.of(() -> adminAccountService.login(
                request.getUserName(), request.getPassword()));
    }
}

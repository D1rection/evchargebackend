package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.AdminAccountRequest;
import bupt.evchargebackend.service.admin.AdminAccountService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员账号 Controller。
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

    /** 管理员注册 */
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody AdminAccountRequest request) {
        Map<String, Object> result = adminAccountService.register(
                request.getUserName(), request.getPassword());
        return Result.success(result);
    }

    /** 管理员登录 */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody AdminAccountRequest request) {
        Map<String, Object> result = adminAccountService.login(
                request.getUserName(), request.getPassword());
        return Result.success(result);
    }
}

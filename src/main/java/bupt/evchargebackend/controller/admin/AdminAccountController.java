package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.AdminAccountRequest;
import bupt.evchargebackend.service.admin.AdminAccountService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/account")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    public AdminAccountController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    /**
     * 管理员注册：创建新的管理员账户。
     *
     * @param request 注册请求（userName / password）
     * @return 包含 userId、userName、role 的结果
     */
    @PostMapping("/create")
    public Result<Map<String, Object>> create(@RequestBody AdminAccountRequest request) {
        return adminAccountService.register(request.getUserName(), request.getPassword());
    }

    /**
     * 管理员登录：验证用户名密码，返回 JWT token。
     *
     * @param request 登录请求（userName / password）
     * @return 包含 userName、token 的结果
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody AdminAccountRequest request) {
        return adminAccountService.login(request.getUserName(), request.getPassword());
    }
}

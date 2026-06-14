package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.service.admin.AdminPricingService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员计费规则 Controller。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@RestController
@RequestMapping("/admin")
public class AdminPricingController {

    private final AdminPricingService adminPricingService;

    public AdminPricingController(AdminPricingService adminPricingService) {
        this.adminPricingService = adminPricingService;
    }

    /** 获取当前全局计费规则 */
    @GetMapping("/pricing")
    public Result<Map<String, Object>> getPricing() {
        return Result.success(adminPricingService.getPricing());
    }
}

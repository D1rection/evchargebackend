package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.service.admin.AdminPricingService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员计费规则 Controller。
 * <p>
 * 提供分时段电费和服务费的查询接口。
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

    /**
     * 获取当前全局计费规则。
     * <p>
     * 返回峰/平/谷各时段的电价、起止时间和服务费单价。
     *
     * @return 计费规则数据 Map，包含 {@code peakStart}、{@code peakPrice} 等字段
     */
    @GetMapping("/pricing")
    public Result<Map<String, Object>> getPricing() {
        return Result.success(adminPricingService.getPricing());
    }
}

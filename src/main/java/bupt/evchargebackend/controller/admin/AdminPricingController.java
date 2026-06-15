package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.PricingRuleRequest;
import bupt.evchargebackend.service.admin.AdminPricingService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminPricingController {

    private final AdminPricingService adminPricingService;

    public AdminPricingController(AdminPricingService adminPricingService) {
        this.adminPricingService = adminPricingService;
    }

    /**
     * 获取当前计费策略：峰/平/谷电价、服务费。
     *
     * @return 计费策略数据（peak/normal/valley 电价及服务费）
     */
    @GetMapping("/pricing")
    public Result<Map<String, Object>> getPricing() {
        return adminPricingService.getPricing();
    }

    /**
     * 设置计费规则：管理员设置指定类型充电桩的分时电价。
     *
     * @param request 计费规则请求（type、各时段起止时间和电价、服务费率）
     * @return 空结果
     */
    @PostMapping("/piles/set-parameters")
    public Result<Void> setParameters(@RequestBody PricingRuleRequest request) {
        return adminPricingService.setPricingRule(request);
    }
}

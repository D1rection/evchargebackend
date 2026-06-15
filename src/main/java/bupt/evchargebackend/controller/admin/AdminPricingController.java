package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.response.Result;
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

    @GetMapping("/pricing")
    public Result<Map<String, Object>> getPricing() {
        return Result.of(() -> adminPricingService.getPricing());
    }
}

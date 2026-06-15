package bupt.evchargebackend.service.admin;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.PricingRuleRequest;

import java.util.Map;

public interface AdminPricingService {
    Result<Map<String, Object>> getPricing();
    Result<Void> setPricingRule(PricingRuleRequest request);
}

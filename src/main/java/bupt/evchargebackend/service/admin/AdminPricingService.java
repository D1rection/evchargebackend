package bupt.evchargebackend.service.admin;

import bupt.evchargebackend.common.response.Result;

import java.util.Map;

public interface AdminPricingService {
    Result<Map<String, Object>> getPricing();
}

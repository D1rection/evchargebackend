package bupt.evchargebackend.service.admin;

import bupt.evchargebackend.common.response.Result;

import java.util.Map;

/**
 * 管理员计费规则服务。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
public interface AdminPricingService {

    /**
     * 获取当前全局计费规则。
     *
     * @return 计费规则数据 Map
     */
    Result<Map<String, Object>> getPricing();
}

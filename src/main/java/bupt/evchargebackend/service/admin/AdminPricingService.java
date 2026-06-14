package bupt.evchargebackend.service.admin;

import java.util.Map;

/**
 * 管理员计费规则服务。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
public interface AdminPricingService {

    /** 获取当前全局计费规则 */
    Map<String, Object> getPricing();
}

package bupt.evchargebackend.service.admin;

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
     * <p>
     * 返回峰/平/谷各时段电价、起止时间和服务费单价。
     *
     * @return 计费规则数据 Map
     */
    Map<String, Object> getPricing();
}

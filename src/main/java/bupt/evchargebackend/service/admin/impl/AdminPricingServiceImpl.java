package bupt.evchargebackend.service.admin.impl;

import bupt.evchargebackend.entity.pricing.BillingRatePeriod;
import bupt.evchargebackend.entity.pricing.enums.PeriodName;
import bupt.evchargebackend.mapper.pricing.BillingRatePeriodMapper;
import bupt.evchargebackend.service.admin.AdminPricingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 管理员计费规则服务实现。
 * <p>
 * 使用 MyBatis Plus 查询 {@link BillingRatePeriod} 表，
 * 按峰/平/谷时段组装计费规则数据。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@Service
public class AdminPricingServiceImpl implements AdminPricingService {

    private final BillingRatePeriodMapper billingRatePeriodMapper;

    public AdminPricingServiceImpl(BillingRatePeriodMapper billingRatePeriodMapper) {
        this.billingRatePeriodMapper = billingRatePeriodMapper;
    }

    /**
     * 获取当前全局计费规则。
     * <p>
     * 查询所有计费时段记录，按 {@link PeriodName} 枚举分类组装为峰/平/谷时段数据。
     *
     * @return 计费规则 Map，含 {@code peakStart/peakEnd/peakPrice}、
     *         {@code normalStart/normalEnd/normalPrice}、
     *         {@code valleyStart/valleyEnd/valleyPrice} 和 {@code serviceFeeValue}
     */
    @Override
    public Map<String, Object> getPricing() {
        List<BillingRatePeriod> periods = billingRatePeriodMapper.selectList(null);

        Map<String, Object> result = new LinkedHashMap<>();
        double serviceFeeValue = 0.0;

        for (BillingRatePeriod p : periods) {
            serviceFeeValue = p.getServicePrice().doubleValue();

            switch (p.getPeriodName()) {
                case PEAK -> {
                    result.put("peakStart", p.getStartTime());
                    result.put("peakEnd", p.getEndTime());
                    result.put("peakPrice", p.getElectricityPrice().doubleValue());
                }
                case NORMAL -> {
                    result.put("normalStart", p.getStartTime());
                    result.put("normalEnd", p.getEndTime());
                    result.put("normalPrice", p.getElectricityPrice().doubleValue());
                }
                case VALLEY -> {
                    result.put("valleyStart", p.getStartTime());
                    result.put("valleyEnd", p.getEndTime());
                    result.put("valleyPrice", p.getElectricityPrice().doubleValue());
                }
            }
        }

        result.put("serviceFeeValue", serviceFeeValue);

        return result;
    }
}

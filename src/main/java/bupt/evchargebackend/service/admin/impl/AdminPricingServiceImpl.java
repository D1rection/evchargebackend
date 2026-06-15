package bupt.evchargebackend.service.admin.impl;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.entity.pricing.BillingRatePeriod;
import bupt.evchargebackend.mapper.pricing.BillingRatePeriodMapper;
import bupt.evchargebackend.service.admin.AdminPricingService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AdminPricingServiceImpl implements AdminPricingService {

    private final BillingRatePeriodMapper billingRatePeriodMapper;

    public AdminPricingServiceImpl(BillingRatePeriodMapper billingRatePeriodMapper) {
        this.billingRatePeriodMapper = billingRatePeriodMapper;
    }

    @Override
    public Result<Map<String, Object>> getPricing() {
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
        return Result.success(result);
    }
}

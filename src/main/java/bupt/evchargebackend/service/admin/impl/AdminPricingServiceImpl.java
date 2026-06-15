package bupt.evchargebackend.service.admin.impl;

import bupt.evchargebackend.common.exception.BusinessException;
import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.PricingRuleRequest;
import bupt.evchargebackend.entity.pile.enums.PileType;
import bupt.evchargebackend.entity.pricing.BillingRatePeriod;
import bupt.evchargebackend.entity.pricing.enums.PeriodName;
import bupt.evchargebackend.mapper.pricing.BillingRatePeriodMapper;
import bupt.evchargebackend.service.admin.AdminPricingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    @Override
    public Result<Void> setPricingRule(PricingRuleRequest request) {
        // 校验类型
        PileType pileType;
        try {
            pileType = PileType.valueOf(request.getType().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(400, "充电桩类型无效，必须为 FAST 或 SLOW");
        }

        // 校验必填字段
        if (isBlank(request.getPeakStart()) || isBlank(request.getPeakEnd()) || request.getPeakPrice() == null
                || isBlank(request.getNormalStart()) || isBlank(request.getNormalEnd()) || request.getNormalPrice() == null
                || isBlank(request.getValleyStart()) || isBlank(request.getValleyEnd()) || request.getValleyPrice() == null
                || request.getServiceFeeRate() == null) {
            throw new BusinessException(400, "所有计费参数均为必填");
        }

        // 删除该类型旧数据
        billingRatePeriodMapper.delete(
                new LambdaQueryWrapper<BillingRatePeriod>()
                        .eq(BillingRatePeriod::getPileType, pileType));

        // 插入三条新时段
        String[] names = {"PEAK", "NORMAL", "VALLEY"};
        String[] starts = {request.getPeakStart(), request.getNormalStart(), request.getValleyStart()};
        String[] ends = {request.getPeakEnd(), request.getNormalEnd(), request.getValleyEnd()};
        double[] prices = {request.getPeakPrice(), request.getNormalPrice(), request.getValleyPrice()};

        for (int i = 0; i < 3; i++) {
            BillingRatePeriod period = new BillingRatePeriod();
            period.setPeriodId(UUID.randomUUID().toString());
            period.setPileType(pileType);
            period.setPeriodName(PeriodName.valueOf(names[i]));
            period.setStartTime(starts[i]);
            period.setEndTime(ends[i]);
            period.setElectricityPrice(BigDecimal.valueOf(prices[i]));
            period.setServicePrice(BigDecimal.valueOf(request.getServiceFeeRate()));
            billingRatePeriodMapper.insert(period);
        }

        return Result.success();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

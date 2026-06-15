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

        // 加载该类型已有数据
        List<BillingRatePeriod> existing = billingRatePeriodMapper.selectList(
                new LambdaQueryWrapper<BillingRatePeriod>()
                        .eq(BillingRatePeriod::getPileType, pileType));
        Map<PeriodName, BillingRatePeriod> existingMap = new LinkedHashMap<>();
        for (BillingRatePeriod p : existing) {
            existingMap.put(p.getPeriodName(), p);
        }

        // 处理三个时段：全传则更新/新增，全不传则保留原值
        processPeriod(pileType, PeriodName.PEAK,
                request.getPeakStart(), request.getPeakEnd(), request.getPeakPrice(),
                request.getServiceFeeRate(), existingMap);
        processPeriod(pileType, PeriodName.NORMAL,
                request.getNormalStart(), request.getNormalEnd(), request.getNormalPrice(),
                request.getServiceFeeRate(), existingMap);
        processPeriod(pileType, PeriodName.VALLEY,
                request.getValleyStart(), request.getValleyEnd(), request.getValleyPrice(),
                request.getServiceFeeRate(), existingMap);

        // 仅更新 serviceFeeRate：已存在但本次未重设的时段
        if (request.getServiceFeeRate() != null) {
            for (BillingRatePeriod p : existing) {
                if (p.getPileType() == pileType) {
                    p.setServicePrice(BigDecimal.valueOf(request.getServiceFeeRate()));
                    billingRatePeriodMapper.updateById(p);
                }
            }
        }

        return Result.success();
    }

    /** 处理单个时段：全传则插入/更新，全不传则保留，部分传报错。 */
    private void processPeriod(PileType pileType, PeriodName name,
                               String start, String end, Double price,
                               Double serviceFeeRate,
                               Map<PeriodName, BillingRatePeriod> existingMap) {
        boolean hasStart = !isBlank(start);
        boolean hasEnd = !isBlank(end);
        boolean hasPrice = price != null;

        if (!hasStart && !hasEnd && !hasPrice) {
            return; // 不传则保留原值
        }
        if (!hasStart || !hasEnd || !hasPrice) {
            throw new BusinessException(400,
                    name + " 时段的 start、end、price 需同时提供或同时省略");
        }

        BillingRatePeriod period = existingMap.get(name);
        if (period == null) {
            period = new BillingRatePeriod();
            period.setPeriodId(UUID.randomUUID().toString());
            period.setPileType(pileType);
            period.setPeriodName(name);
        }
        period.setStartTime(start);
        period.setEndTime(end);
        period.setElectricityPrice(BigDecimal.valueOf(price));
        if (serviceFeeRate != null) {
            period.setServicePrice(BigDecimal.valueOf(serviceFeeRate));
        }
        billingRatePeriodMapper.insertOrUpdate(period);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

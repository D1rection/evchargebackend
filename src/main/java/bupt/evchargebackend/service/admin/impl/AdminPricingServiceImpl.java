package bupt.evchargebackend.service.admin.impl;

import bupt.evchargebackend.common.exception.BusinessException;
import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.PricingRuleRequest;
import bupt.evchargebackend.dto.PricingRuleRequest.PeriodConfig;
import bupt.evchargebackend.entity.pile.enums.PileType;
import bupt.evchargebackend.entity.pricing.BillingRatePeriod;
import bupt.evchargebackend.entity.pricing.enums.PeriodName;
import bupt.evchargebackend.mapper.pricing.BillingRatePeriodMapper;
import bupt.evchargebackend.service.admin.AdminPricingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        // 按类型分组
        Map<PileType, List<BillingRatePeriod>> grouped = new LinkedHashMap<>();
        for (BillingRatePeriod p : periods) {
            grouped.computeIfAbsent(p.getPileType(), k -> new ArrayList<>()).add(p);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (PileType type : PileType.values()) {
            List<BillingRatePeriod> list = grouped.getOrDefault(type, List.of());
            Map<String, Object> typeData = new LinkedHashMap<>();

            // 时段列表
            List<Map<String, Object>> periodList = new ArrayList<>();
            double serviceFeeValue = 0.0;

            for (BillingRatePeriod p : list) {
                serviceFeeValue = p.getServicePrice().doubleValue();
                Map<String, Object> periodData = new LinkedHashMap<>();
                periodData.put("periodName", p.getPeriodName().name());
                periodData.put("startTime", p.getStartTime());
                periodData.put("endTime", p.getEndTime());
                periodData.put("electricityPrice", p.getElectricityPrice().doubleValue());
                periodList.add(periodData);
            }

            typeData.put("periods", periodList);
            typeData.put("serviceFee", serviceFeeValue);
            result.put(type.name(), typeData);
        }

        return Result.success(result);
    }

    @Override
    @Transactional
    public Result<Void> setPricingRule(PricingRuleRequest request) {
        // 1. 校验类型
        PileType pileType;
        try {
            pileType = PileType.valueOf(request.getType().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(400, "充电桩类型无效，必须为 FAST 或 SLOW");
        }

        List<PeriodConfig> configs = request.getPeriods();
        if (configs == null || configs.isEmpty()) {
            throw new BusinessException(400, "时段列表不能为空");
        }

        // 2. 校验每个时段字段完整 + periodName 合法
        for (int i = 0; i < configs.size(); i++) {
            PeriodConfig c = configs.get(i);
            if (isBlank(c.getPeriodName())) {
                throw new BusinessException(400, "第" + (i + 1) + "个时段 periodName 不能为空");
            }
            try {
                PeriodName.valueOf(c.getPeriodName().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(400,
                        "第" + (i + 1) + "个时段 periodName 无效，必须为 PEAK / NORMAL / VALLEY");
            }
            if (isBlank(c.getStartTime()) || isBlank(c.getEndTime())) {
                throw new BusinessException(400,
                        "第" + (i + 1) + "个时段 startTime 和 endTime 不能为空");
            }
            if (!c.getStartTime().matches("\\d{2}:\\d{2}") || !c.getEndTime().matches("\\d{2}:\\d{2}")) {
                throw new BusinessException(400,
                        "第" + (i + 1) + "个时段时间格式必须为 HH:mm");
            }
            if (c.getElectricityPrice() == null || c.getElectricityPrice() <= 0) {
                throw new BusinessException(400,
                        "第" + (i + 1) + "个时段 electricityPrice 必须大于 0");
            }
        }

        // 3. 时段重叠检测
        validateNoOverlap(configs);

        // 4. 覆盖式更新：先删后插
        billingRatePeriodMapper.delete(
                new LambdaQueryWrapper<BillingRatePeriod>()
                        .eq(BillingRatePeriod::getPileType, pileType));

        double serviceFee = request.getServiceFeeRate() != null ? request.getServiceFeeRate() : 0.0;

        for (PeriodConfig c : configs) {
            BillingRatePeriod period = new BillingRatePeriod();
            period.setPeriodId(UUID.randomUUID().toString());
            period.setPileType(pileType);
            period.setPeriodName(PeriodName.valueOf(c.getPeriodName().toUpperCase()));
            period.setStartTime(c.getStartTime());
            period.setEndTime(c.getEndTime());
            period.setElectricityPrice(BigDecimal.valueOf(c.getElectricityPrice()));
            period.setServicePrice(BigDecimal.valueOf(serviceFee));
            billingRatePeriodMapper.insert(period);
        }

        return Result.success();
    }

    /**
     * 检测时段列表是否存在重叠（含跨午夜场景）。
     * 使用半开区间 [start, end)，相邻时段如 10:00 结束和 10:00 开始不视为重叠。
     */
    static void validateNoOverlap(List<PeriodConfig> configs) {
        for (int i = 0; i < configs.size(); i++) {
            for (int j = i + 1; j < configs.size(); j++) {
                if (overlaps(configs.get(i), configs.get(j))) {
                    PeriodConfig a = configs.get(i);
                    PeriodConfig b = configs.get(j);
                    throw new BusinessException(400,
                            String.format("时段冲突：%s %s-%s 与 %s %s-%s 存在重叠",
                                    a.getPeriodName(), a.getStartTime(), a.getEndTime(),
                                    b.getPeriodName(), b.getStartTime(), b.getEndTime()));
                }
            }
        }
    }

    /**
     * 判断两个时段是否重叠。
     *
     * @param a 时段A
     * @param b 时段B
     * @return true 表示存在重叠
     */
    static boolean overlaps(PeriodConfig a, PeriodConfig b) {
        int s1 = parseMinutes(a.getStartTime());
        int e1 = parseMinutes(a.getEndTime());
        int s2 = parseMinutes(b.getStartTime());
        int e2 = parseMinutes(b.getEndTime());

        boolean aCross = s1 >= e1; // 跨午夜
        boolean bCross = s2 >= e2;

        if (aCross && bCross) {
            // 两者都跨午夜，必然有重叠（都覆盖了午夜附近）
            return true;
        }

        if (aCross) {
            // A跨午夜 = [s1, 1440) ∪ [0, e1)，B不跨午夜 = [s2, e2)
            return s1 < e2 || s2 < e1;
        }

        if (bCross) {
            // B跨午夜，A不跨午夜
            return s2 < e1 || s1 < e2;
        }

        // 都不跨午夜：标准半开区间重叠判断
        return s1 < e2 && s2 < e1;
    }

    private static int parseMinutes(String time) {
        String[] parts = time.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

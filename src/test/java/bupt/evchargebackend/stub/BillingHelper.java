package bupt.evchargebackend.stub;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;

/**
 * 计费计算：分时电价 + 服务费。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
public class BillingHelper {

    private static final LocalTime VALLEY_END = LocalTime.of(7, 0);
    private static final LocalTime NORMAL_END = LocalTime.of(8, 0);

    private static final BigDecimal VALLEY_RATE = new BigDecimal("0.4");
    private static final BigDecimal NORMAL_RATE = new BigDecimal("0.7");
    private static final BigDecimal PEAK_RATE = new BigDecimal("1.0");
    private static final BigDecimal SERVICE_FEE = new BigDecimal("0.8");

    /** 快充功率 kWh/h */
    public static final BigDecimal FAST_POWER = new BigDecimal("30");
    /** 慢充功率 kWh/h */
    public static final BigDecimal SLOW_POWER = new BigDecimal("10");

    /**
     * 计算电费单价（含服务费）。
     */
    public static BigDecimal rateAt(LocalTime time) {
        if (time.isBefore(VALLEY_END)) {
            return VALLEY_RATE.add(SERVICE_FEE); // 1.20
        } else if (time.isBefore(NORMAL_END)) {
            return NORMAL_RATE.add(SERVICE_FEE); // 1.50
        } else {
            return PEAK_RATE.add(SERVICE_FEE);   // 1.80
        }
    }

    /**
     * 计算充电增量（kWh）。
     *
     * @param powerKw    桩功率（kWh/h）
     * @param minutes    充电时长（分钟）
     * @return 增加的充电量
     */
    public static BigDecimal chargeIncrement(BigDecimal powerKw, int minutes) {
        return powerKw.multiply(BigDecimal.valueOf(minutes))
                .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP);
    }

    /**
     * 计算一段固定时长内的费用。
     * 假设整段时长内电价不变。
     */
    public static BigDecimal feeFor(BigDecimal kwh, LocalTime time) {
        return kwh.multiply(rateAt(time)).setScale(2, RoundingMode.HALF_UP);
    }
}

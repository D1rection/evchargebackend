package bupt.evchargebackend.schedule.stub;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;

/**
 * 计费计算：分时电价 + 服务费。
 *
 * 谷时 0.4（23:00~7:00）
 * 平时 0.7（7:00~10:00, 15:00~18:00, 21:00~23:00）
 * 峰时 1.0（10:00~15:00, 18:00~21:00）
 * 服务费 0.8 元/kWh
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
public class BillingHelper {

    private static final BigDecimal VALLEY_RATE = new BigDecimal("0.4");
    private static final BigDecimal NORMAL_RATE = new BigDecimal("0.7");
    private static final BigDecimal PEAK_RATE = new BigDecimal("1.0");
    private static final BigDecimal SERVICE_FEE = new BigDecimal("0.8");

    public static final BigDecimal FAST_POWER = new BigDecimal("30");
    public static final BigDecimal SLOW_POWER = new BigDecimal("10");

    public static BigDecimal rateAt(LocalTime time) {
        int h = time.getHour();
        if (h >= 23 || h < 7) return VALLEY_RATE.add(SERVICE_FEE);
        if (h >= 7 && h < 10) return NORMAL_RATE.add(SERVICE_FEE);
        if (h >= 10 && h < 15) return PEAK_RATE.add(SERVICE_FEE);
        if (h >= 15 && h < 18) return NORMAL_RATE.add(SERVICE_FEE);
        if (h >= 18 && h < 21) return PEAK_RATE.add(SERVICE_FEE);
        return NORMAL_RATE.add(SERVICE_FEE);
    }

    public static BigDecimal chargeIncrement(BigDecimal powerKw, int minutes) {
        return powerKw.multiply(BigDecimal.valueOf(minutes))
                .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP);
    }

    public static BigDecimal feeFor(BigDecimal kwh, LocalTime time) {
        return kwh.multiply(rateAt(time)).setScale(2, RoundingMode.HALF_UP);
    }
}

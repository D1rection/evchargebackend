package bupt.evchargebackend.dto;

import java.util.List;

/**
 * 管理员设置计费规则请求。
 * <p>
 * 支持同一电价等级（PEAK/NORMAL/VALLEY）配置多个不连续的时间窗口，
 * 例如峰时可以是 08:00-10:00 和 19:00-21:00。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
public class PricingRuleRequest {

    /** 充电桩类型：FAST（快充）/ SLOW（慢充） */
    private String type;

    /** 时段列表，每个时段包含名称、起止时间、电价 */
    private List<PeriodConfig> periods;

    /** 服务费率（元/kWh），所有时段统一 */
    private Double serviceFeeRate;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<PeriodConfig> getPeriods() { return periods; }
    public void setPeriods(List<PeriodConfig> periods) { this.periods = periods; }

    public Double getServiceFeeRate() { return serviceFeeRate; }
    public void setServiceFeeRate(Double serviceFeeRate) { this.serviceFeeRate = serviceFeeRate; }

    /**
     * 单个计费时段配置。
     */
    public static class PeriodConfig {

        /** 时段名称：PEAK / NORMAL / VALLEY */
        private String periodName;

        /** 时段开始时间（HH:mm） */
        private String startTime;

        /** 时段结束时间（HH:mm） */
        private String endTime;

        /** 电价（元/kWh） */
        private Double electricityPrice;

        public String getPeriodName() { return periodName; }
        public void setPeriodName(String periodName) { this.periodName = periodName; }

        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }

        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }

        public Double getElectricityPrice() { return electricityPrice; }
        public void setElectricityPrice(Double electricityPrice) { this.electricityPrice = electricityPrice; }
    }
}

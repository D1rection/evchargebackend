package bupt.evchargebackend.dto;

/**
 * 管理员设置计费规则请求。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
public class PricingRuleRequest {

    /** 充电桩类型：FAST（快充）/ SLOW（慢充） */
    private String type;

    /** 高峰时段起始（HH:mm） */
    private String peakStart;
    /** 高峰时段结束（HH:mm） */
    private String peakEnd;
    /** 高峰电价（元/kWh） */
    private Double peakPrice;

    /** 平时段起始（HH:mm） */
    private String normalStart;
    /** 平时段结束（HH:mm） */
    private String normalEnd;
    /** 平时段电价（元/kWh） */
    private Double normalPrice;

    /** 低谷时段起始（HH:mm） */
    private String valleyStart;
    /** 低谷时段结束（HH:mm） */
    private String valleyEnd;
    /** 低谷电价（元/kWh） */
    private Double valleyPrice;

    /** 服务费率（元/kWh），三个时段统一 */
    private Double serviceFeeRate;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPeakStart() { return peakStart; }
    public void setPeakStart(String peakStart) { this.peakStart = peakStart; }
    public String getPeakEnd() { return peakEnd; }
    public void setPeakEnd(String peakEnd) { this.peakEnd = peakEnd; }
    public Double getPeakPrice() { return peakPrice; }
    public void setPeakPrice(Double peakPrice) { this.peakPrice = peakPrice; }

    public String getNormalStart() { return normalStart; }
    public void setNormalStart(String normalStart) { this.normalStart = normalStart; }
    public String getNormalEnd() { return normalEnd; }
    public void setNormalEnd(String normalEnd) { this.normalEnd = normalEnd; }
    public Double getNormalPrice() { return normalPrice; }
    public void setNormalPrice(Double normalPrice) { this.normalPrice = normalPrice; }

    public String getValleyStart() { return valleyStart; }
    public void setValleyStart(String valleyStart) { this.valleyStart = valleyStart; }
    public String getValleyEnd() { return valleyEnd; }
    public void setValleyEnd(String valleyEnd) { this.valleyEnd = valleyEnd; }
    public Double getValleyPrice() { return valleyPrice; }
    public void setValleyPrice(Double valleyPrice) { this.valleyPrice = valleyPrice; }

    public Double getServiceFeeRate() { return serviceFeeRate; }
    public void setServiceFeeRate(Double serviceFeeRate) { this.serviceFeeRate = serviceFeeRate; }
}

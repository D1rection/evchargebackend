package bupt.evchargebackend.entity.pricing;

import bupt.evchargebackend.entity.pile.enums.PileType;
import bupt.evchargebackend.entity.pricing.enums.PeriodName;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分时电价表。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
@Data
@TableName("billing_rate_period")
public class BillingRatePeriod {

    /** 主键 */
    @TableId
    private String periodId;

    /** 适用桩类型 */
    private PileType pileType;

    /** 时段名称 */
    private PeriodName periodName;

    /** 时段开始时间（HH:mm） */
    private String startTime;

    /** 时段结束时间（HH:mm） */
    private String endTime;

    /** 电价（元/kWh） */
    private BigDecimal electricityPrice;

    /** 服务费（元/kWh） */
    private BigDecimal servicePrice;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    public String getPeriodId() {
        return periodId;
    }

    public void setPeriodId(String periodId) {
        this.periodId = periodId;
    }

    public PileType getPileType() {
        return pileType;
    }

    public void setPileType(PileType pileType) {
        this.pileType = pileType;
    }

    public PeriodName getPeriodName() {
        return periodName;
    }

    public void setPeriodName(PeriodName periodName) {
        this.periodName = periodName;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public BigDecimal getElectricityPrice() {
        return electricityPrice;
    }

    public void setElectricityPrice(BigDecimal electricityPrice) {
        this.electricityPrice = electricityPrice;
    }

    public BigDecimal getServicePrice() {
        return servicePrice;
    }

    public void setServicePrice(BigDecimal servicePrice) {
        this.servicePrice = servicePrice;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

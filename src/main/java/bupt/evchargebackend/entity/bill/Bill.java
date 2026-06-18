package bupt.evchargebackend.entity.bill;

import bupt.evchargebackend.entity.bill.enums.PaymentStatus;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账单表。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
@Data
@TableName("bill")
public class Bill {

    /** 主键 */
    @TableId
    private String billId;

    /** 账单号，对外展示 */
    private String billNo;

    /** 订单ID，外键 */
    private String orderId;

    /** 充电会话ID，外键 */
    private String sessionId;

    /** 车辆ID，外键 */
    private String carId;

    /** 充电桩ID，外键 */
    private String pileId;

    /** 充电桩编号（非表字段） */
    @TableField(exist = false)
    private String pileNo;

    /** 车牌号（非表字段） */
    @TableField(exist = false)
    private String carNo;

    /** 充电开始时间 */
    private LocalDateTime startTime;

    /** 充电结束时间 */
    private LocalDateTime endTime;

    /** 充电量（kWh） */
    private BigDecimal chargedKwh;

    /** 充电时长（分钟） */
    private Integer chargeMinutes;

    /** 电费（元） */
    private BigDecimal electricityFee;

    /** 服务费（元） */
    private BigDecimal serviceFee;

    /** 总费用（元） */
    private BigDecimal totalFee;

    /** 支付状态 */
    private PaymentStatus paymentStatus;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public String getBillId() {
        return billId;
    }

    public void setBillId(String billId) {
        this.billId = billId;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getCarId() {
        return carId;
    }

    public void setCarId(String carId) {
        this.carId = carId;
    }

    public String getPileId() {
        return pileId;
    }

    public void setPileId(String pileId) {
        this.pileId = pileId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public BigDecimal getChargedKwh() {
        return chargedKwh;
    }

    public void setChargedKwh(BigDecimal chargedKwh) {
        this.chargedKwh = chargedKwh;
    }

    public Integer getChargeMinutes() {
        return chargeMinutes;
    }

    public void setChargeMinutes(Integer chargeMinutes) {
        this.chargeMinutes = chargeMinutes;
    }

    public BigDecimal getElectricityFee() {
        return electricityFee;
    }

    public void setElectricityFee(BigDecimal electricityFee) {
        this.electricityFee = electricityFee;
    }

    public BigDecimal getServiceFee() {
        return serviceFee;
    }

    public void setServiceFee(BigDecimal serviceFee) {
        this.serviceFee = serviceFee;
    }

    public BigDecimal getTotalFee() {
        return totalFee;
    }

    public void setTotalFee(BigDecimal totalFee) {
        this.totalFee = totalFee;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

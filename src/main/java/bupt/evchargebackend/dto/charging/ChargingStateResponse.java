package bupt.evchargebackend.dto.charging;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 充电状态响应 VO。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@Data
public class ChargingStateResponse {

    /** 车辆 ID */
    private String carId;
    /** 充电状态：none / charging / interrupted / completed */
    private String status;
    /** 充电订单 ID */
    private String orderId;
    /** 充电桩 ID */
    private String pileNum;
    /** 充电开始时间 */
    private String startTime;
    /** 已充电时长 HH:mm:ss */
    private String currentDuration;
    /** 目标充电量 kWh */
    private BigDecimal requestAmount;
    /** 当前累计充电量 kWh */
    private BigDecimal currentAmount;
    /** 当前电费（元） */
    private BigDecimal currentChargeFee;
    /** 当前服务费（元） */
    private BigDecimal currentServiceFee;
    /** 当前总费用（元） */
    private BigDecimal totalCurrentFee;
    /** 当前时段电价（元/kWh） */
    private BigDecimal currentPeriodPrice;
    /** 下一时段电价（元/kWh） */
    private BigDecimal nextPeriodPrice;
    public String getCarId() {
        return carId;
    }

    public void setCarId(String carId) {
        this.carId = carId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getPileNum() {
        return pileNum;
    }

    public void setPileNum(String pileNum) {
        this.pileNum = pileNum;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getCurrentDuration() {
        return currentDuration;
    }

    public void setCurrentDuration(String currentDuration) {
        this.currentDuration = currentDuration;
    }

    public BigDecimal getRequestAmount() {
        return requestAmount;
    }

    public void setRequestAmount(BigDecimal requestAmount) {
        this.requestAmount = requestAmount;
    }

    public BigDecimal getCurrentAmount() {
        return currentAmount;
    }

    public void setCurrentAmount(BigDecimal currentAmount) {
        this.currentAmount = currentAmount;
    }

    public BigDecimal getCurrentChargeFee() {
        return currentChargeFee;
    }

    public void setCurrentChargeFee(BigDecimal currentChargeFee) {
        this.currentChargeFee = currentChargeFee;
    }

    public BigDecimal getCurrentServiceFee() {
        return currentServiceFee;
    }

    public void setCurrentServiceFee(BigDecimal currentServiceFee) {
        this.currentServiceFee = currentServiceFee;
    }

    public BigDecimal getTotalCurrentFee() {
        return totalCurrentFee;
    }

    public void setTotalCurrentFee(BigDecimal totalCurrentFee) {
        this.totalCurrentFee = totalCurrentFee;
    }

    public BigDecimal getCurrentPeriodPrice() {
        return currentPeriodPrice;
    }

    public void setCurrentPeriodPrice(BigDecimal currentPeriodPrice) {
        this.currentPeriodPrice = currentPeriodPrice;
    }

    public BigDecimal getNextPeriodPrice() {
        return nextPeriodPrice;
    }

    public void setNextPeriodPrice(BigDecimal nextPeriodPrice) {
        this.nextPeriodPrice = nextPeriodPrice;
    }
}

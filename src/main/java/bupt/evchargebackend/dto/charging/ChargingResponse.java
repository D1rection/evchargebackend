package bupt.evchargebackend.dto.charging;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 充电申请响应 VO。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@Data
public class ChargingResponse {

    /** 车辆位置：等候区 / 充电区 */
    private String carPosition;
    /** 车辆状态：waiting / called / charging / done */
    private String carState;
    /** 排队序号 */
    private Integer queueNum;
    /** 请求提交时间 */
    private String requestTime;
    /** 预估费用（元） */
    private BigDecimal estimatedFee;
    /** 预估用时（分钟） */
    private Integer estimatedMinutes;
    public String getCarPosition() {
        return carPosition;
    }

    public void setCarPosition(String carPosition) {
        this.carPosition = carPosition;
    }

    public String getCarState() {
        return carState;
    }

    public void setCarState(String carState) {
        this.carState = carState;
    }

    public Integer getQueueNum() {
        return queueNum;
    }

    public void setQueueNum(Integer queueNum) {
        this.queueNum = queueNum;
    }

    public String getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(String requestTime) {
        this.requestTime = requestTime;
    }

    public BigDecimal getEstimatedFee() {
        return estimatedFee;
    }

    public void setEstimatedFee(BigDecimal estimatedFee) {
        this.estimatedFee = estimatedFee;
    }

    public Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public void setEstimatedMinutes(Integer estimatedMinutes) {
        this.estimatedMinutes = estimatedMinutes;
    }
}

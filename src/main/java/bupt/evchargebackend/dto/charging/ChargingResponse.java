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
}

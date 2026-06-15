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
}

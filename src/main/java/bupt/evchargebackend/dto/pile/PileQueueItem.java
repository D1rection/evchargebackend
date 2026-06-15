package bupt.evchargebackend.dto.pile;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 充电桩排队条目 VO。
 *
 * @author Deng Chao
 * @since 2026-06-16
 */
@Data
public class PileQueueItem {

    /** 车辆 ID */
    private String carId;
    /** 电池容量 kWh */
    private BigDecimal carCapacity;
    /** 请求充电量 kWh */
    private BigDecimal requestAmount;
    /** 预计等待时间 HH:mm:ss */
    private String waitTime;
    /** 排队位置（1-based） */
    private Integer queuePosition;
}

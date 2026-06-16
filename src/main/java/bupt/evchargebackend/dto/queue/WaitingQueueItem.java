package bupt.evchargebackend.dto.queue;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 等候区队列条目 VO。
 *
 * @author Deng Chao
 * @since 2026-06-16
 */
@Data
public class WaitingQueueItem {

    /** 车辆 ID */
    private String carId;
    /** 电池容量 kWh */
    private BigDecimal carCapacity;
    /** 请求充电量 kWh */
    private BigDecimal requestAmount;
    /** 已等待时间 HH:mm:ss */
    private String waitTime;
    /** 充电模式 FAST / SLOW（ALL 模式时用于区分） */
    private String requestMode;
}

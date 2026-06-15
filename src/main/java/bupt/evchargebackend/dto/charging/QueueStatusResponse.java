package bupt.evchargebackend.dto.charging;

import lombok.Data;

/**
 * 车辆队列状态响应 VO。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@Data
public class QueueStatusResponse {

    /** 车辆状态：waiting / called / charging / done */
    private String carState;
    /** 排队序号 */
    private Integer queueNum;
    /** 前方车辆数 */
    private Integer carNumberBeforePosition;
    /** 请求提交时间 */
    private String requestTime;
    /** 分配的充电桩 ID（called/charging 时有值） */
    private String assignedPileNum;
}

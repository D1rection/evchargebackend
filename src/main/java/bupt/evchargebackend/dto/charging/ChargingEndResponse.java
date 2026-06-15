package bupt.evchargebackend.dto.charging;

import lombok.Data;

/**
 * 结束充电响应 VO。
 *
 * @author Deng Chao
 * @since 2026-06-16
 */
@Data
public class ChargingEndResponse {

    /** 操作结果：1=成功，0=失败 */
    private Integer result;
}

package bupt.evchargebackend.dto.charging;

import lombok.Data;

/**
 * 开始充电响应 VO。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@Data
public class ChargingStartResponse {

    /** 操作结果：1=成功，0=失败 */
    private Integer result;
}

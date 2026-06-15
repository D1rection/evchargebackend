package bupt.evchargebackend.dto.charging;

import lombok.Data;

/**
 * 取消充电申请请求 DTO。
 *
 * @author Deng Chao
 * @since 2026-06-16
 */
@Data
public class ChargingCancelRequest {

    private String carId;
}

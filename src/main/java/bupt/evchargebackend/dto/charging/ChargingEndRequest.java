package bupt.evchargebackend.dto.charging;

import lombok.Data;

/**
 * 结束充电请求 DTO。
 *
 * @author Deng Chao
 * @since 2026-06-16
 */
@Data
public class ChargingEndRequest {

    private String carId;
    private String chargingPileNum;
}

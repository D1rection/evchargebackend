package bupt.evchargebackend.dto.charging;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 充电申请请求 DTO。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@Data
public class ChargingRequest {

    private String carId;
    private BigDecimal requestAmount;
    private String requestMode;

    public String resolveCarId() {
        return carId;
    }

    public BigDecimal resolveAmount() {
        return requestAmount;
    }

    public String resolveMode() {
        return requestMode;
    }
}

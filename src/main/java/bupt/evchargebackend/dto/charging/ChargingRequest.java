package bupt.evchargebackend.dto.charging;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 充电申请请求 DTO：兼容 camelCase / PascalCase / snake_case 字段映射。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@Data
public class ChargingRequest {

    private String carId;
    private String car_Id;
    private String car_id;
    private BigDecimal requestAmount;
    private BigDecimal RequestAmount;
    private String requestMode;
    private String RequestMode;

    public String resolveCarId() {
        if (hasText(carId)) {
            return carId;
        }
        if (hasText(car_Id)) {
            return car_Id;
        }
        return car_id;
    }

    public BigDecimal resolveAmount() {
        if (requestAmount != null) {
            return requestAmount;
        }
        return RequestAmount;
    }

    public String resolveMode() {
        if (hasText(requestMode)) {
            return requestMode;
        }
        return RequestMode;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

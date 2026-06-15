package bupt.evchargebackend.dto.charging;

import lombok.Data;

/**
 * 开始充电请求 DTO。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@Data
public class ChargingStartRequest {

    private String carId;
    private String chargePileNum;
    public String getCarId() {
        return carId;
    }

    public void setCarId(String carId) {
        this.carId = carId;
    }

    public String getChargePileNum() {
        return chargePileNum;
    }

    public void setChargePileNum(String chargePileNum) {
        this.chargePileNum = chargePileNum;
    }
}

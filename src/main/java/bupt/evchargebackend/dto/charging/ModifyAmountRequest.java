package bupt.evchargebackend.dto.charging;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ModifyAmountRequest {

    private String carId;
    private String car_Id;
    private String car_id;
    private BigDecimal amount;
    private BigDecimal Amount;
    private BigDecimal targetKwh;

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
        if (amount != null) {
            return amount;
        }
        if (Amount != null) {
            return Amount;
        }
        return targetKwh;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public String getCarId() {
        return carId;
    }

    public void setCarId(String carId) {
        this.carId = carId;
    }

    public String getCar_Id() {
        return car_Id;
    }

    public void setCar_Id(String car_Id) {
        this.car_Id = car_Id;
    }

    public String getCar_id() {
        return car_id;
    }

    public void setCar_id(String car_id) {
        this.car_id = car_id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getTargetKwh() {
        return targetKwh;
    }

    public void setTargetKwh(BigDecimal targetKwh) {
        this.targetKwh = targetKwh;
    }
}

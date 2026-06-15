package bupt.evchargebackend.dto.charging;

import lombok.Data;

@Data
public class ModifyModeRequest {

    private String carId;
    private String car_Id;
    private String car_id;
    private String mode;
    private String Mode;
    private String requestMode;

    public String resolveCarId() {
        if (hasText(carId)) {
            return carId;
        }
        if (hasText(car_Id)) {
            return car_Id;
        }
        return car_id;
    }

    public String resolveMode() {
        if (hasText(mode)) {
            return mode;
        }
        if (hasText(Mode)) {
            return Mode;
        }
        return requestMode;
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

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getRequestMode() {
        return requestMode;
    }

    public void setRequestMode(String requestMode) {
        this.requestMode = requestMode;
    }
}

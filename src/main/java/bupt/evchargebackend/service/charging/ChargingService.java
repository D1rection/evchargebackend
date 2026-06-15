package bupt.evchargebackend.service.charging;

import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.charging.enums.RequestMode;

import java.math.BigDecimal;

public interface ChargingService {

    ChargingOrder modifyAmount(String carId, BigDecimal amount);

    ChargingOrder modifyMode(String carId, RequestMode requestMode);
}

package bupt.evchargebackend.service.charging;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.charging.ChargingRequest;
import bupt.evchargebackend.dto.charging.ChargingResponse;
import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.charging.enums.RequestMode;

import java.math.BigDecimal;

public interface ChargingService {

    /**
     * 提交充电申请：校验请求参数，计算排队序号和预估费用，返回响应。
     */
    Result<ChargingResponse> submit(ChargingRequest request);

    ChargingOrder modifyAmount(String carId, BigDecimal amount);

    ChargingOrder modifyMode(String carId, RequestMode requestMode);
}

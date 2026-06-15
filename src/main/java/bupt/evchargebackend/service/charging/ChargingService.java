package bupt.evchargebackend.service.charging;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.charging.ChargingRequest;
import bupt.evchargebackend.dto.charging.ChargingResponse;
import bupt.evchargebackend.dto.charging.ChargingStartRequest;
import bupt.evchargebackend.dto.charging.ChargingStartResponse;
import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.charging.enums.RequestMode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ChargingService {

    /**
     * 提交充电申请：校验请求参数，计算排队序号和预估费用，返回响应。
     */
    Result<ChargingResponse> submit(ChargingRequest request);

    /**
     * 开始充电：用户确认后创建充电会话，更新桩和订单状态。
     */
    Result<ChargingStartResponse> start(ChargingStartRequest request);

    /**
     * 查询指定时间对应的分时电价。
     *
     * @param time 时间（HH:mm）
     * @return 匹配的时段列表（通常为 1 条）
     */
    Result<List<Map<String, Object>>> getPeriodByTime(String time);

    ChargingOrder modifyAmount(String carId, BigDecimal amount);

    ChargingOrder modifyMode(String carId, RequestMode requestMode);
}

package bupt.evchargebackend.service.charging;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.charging.ChargingRequest;
import bupt.evchargebackend.dto.charging.ChargingResponse;
import bupt.evchargebackend.dto.charging.ChargingCancelRequest;
import bupt.evchargebackend.dto.charging.ChargingEndRequest;
import bupt.evchargebackend.dto.charging.ChargingEndResponse;
import bupt.evchargebackend.dto.charging.ChargingStateResponse;
import bupt.evchargebackend.dto.charging.ModifyResponse;
import bupt.evchargebackend.dto.charging.QueueStatusResponse;
import bupt.evchargebackend.entity.charging.enums.RequestMode;
import java.math.BigDecimal;
import java.util.Map;

public interface ChargingService {

    /**
     * 提交充电申请：校验请求参数，计算排队序号和预估费用，返回响应。
     */
    Result<ChargingResponse> submit(ChargingRequest request);

    /**
     * 查询指定时间对应的分时电价，按充电桩类型分组。
     *
     * @param time 时间（HH:mm）
     * @return 按 FAST/SLOW 分组的时段电价
     */
    Result<Map<String, Object>> getPeriodByTime(String time);

    /**
     * 查看车辆队列状态：查询车辆在等候区或充电区的排队位置。
     */
    Result<QueueStatusResponse> queueStatus(String carId);

    /**
     * 查看充电状态：查询充电进度、费用和时段电价。
     */
    Result<ChargingStateResponse> chargingState(String carId);

    /**
     * 结束充电：创建账单，释放充电桩。
     */
    Result<ChargingEndResponse> end(ChargingEndRequest request);

    /**
     * 取消充电申请：从等候区或桩队列中移除订单。
     */
    Result<ChargingEndResponse> cancel(ChargingCancelRequest request);

    /**
     * 修改充电量：更新订单的目标充电量并重算预估费用和时长。
     */
    Result<ModifyResponse> modifyAmount(String carId, BigDecimal amount);

    /**
     * 修改充电模式：将订单从当前队列移除，更新模式后重新调度。
     */
    Result<ModifyResponse> modifyMode(String carId, RequestMode requestMode);

    /**
     * 充满自动结束充电（模拟/定时器触发）。
     */
    void autoFinish(String sessionId);

    /**
     * 故障桩恢复后触发补位和自动开始。
     */
    void onPileRecovered(String pileId);
}

package bupt.evchargebackend.controller.charging;

import bupt.evchargebackend.common.exception.BusinessException;
import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.charging.ChargingRequest;
import bupt.evchargebackend.dto.charging.ChargingResponse;
import bupt.evchargebackend.dto.charging.ChargingStartRequest;
import bupt.evchargebackend.dto.charging.ChargingStartResponse;
import bupt.evchargebackend.dto.charging.ChargingCancelRequest;
import bupt.evchargebackend.dto.charging.ChargingEndRequest;
import bupt.evchargebackend.dto.charging.ChargingEndResponse;
import bupt.evchargebackend.dto.charging.ChargingStateResponse;
import bupt.evchargebackend.dto.charging.QueueStatusResponse;
import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.charging.enums.RequestMode;
import bupt.evchargebackend.service.charging.ChargingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/charging")
public class ChargingController {

    private final ChargingService chargingService;

    public ChargingController(ChargingService chargingService) {
        this.chargingService = chargingService;
    }

    /**
     * 提交充电申请：车辆进入等候区后用户发起充电请求。
     */
    @PostMapping("/request")
    public Result<ChargingResponse> submit(@Valid @RequestBody ChargingRequest request) {
        return chargingService.submit(request);
    }

    /**
     * 开始充电：用户确认后开始充电，创建充电会话，更新桩状态。
     */
    @PostMapping("/start")
    public Result<ChargingStartResponse> start(@Valid @RequestBody ChargingStartRequest request) {
        return chargingService.start(request);
    }

    /**
     * 查询指定时间对应的分时电价，按 FAST/SLOW 分组。
     *
     * @param time 时间（HH:mm），如 "14:30"
     * @return 按类型分组的时段电价
     */
    @GetMapping("/periods")
    public Result<Map<String, Object>> getPeriods(@RequestParam String time) {
        return chargingService.getPeriodByTime(time);
    }

    /**
     * 查看车辆队列状态：查询车辆在等候区或充电区的排队位置。
     *
     * @param carId 车辆 ID
     */
    @GetMapping("/queue-position")
    public Result<QueueStatusResponse> queuePosition(@RequestParam String carId) {
        return chargingService.queueStatus(carId);
    }

    /**
     * 查看充电状态：查询车辆当前充电进度、费用和时段电价。
     *
     * @param carId 车辆 ID
     */
    @GetMapping("/state")
    public Result<ChargingStateResponse> state(@RequestParam String carId) {
        return chargingService.chargingState(carId);
    }

    /**
     * 结束充电：用户主动结束或系统自动充满后结束，创建账单并释放充电桩。
     *
     * @param request {carId, chargingPileNum}
     */
    @PostMapping("/end")
    public Result<ChargingEndResponse> end(@Valid @RequestBody ChargingEndRequest request) {
        return chargingService.end(request);
    }

    /**
     * 取消充电申请：用户在等候区或桩队列中取消已提交的申请。
     *
     * @param request {carId}
     */
    @PostMapping("/cancel")
    public Result<ChargingEndResponse> cancel(@Valid @RequestBody ChargingCancelRequest request) {
        return chargingService.cancel(request);
    }

    @PostMapping("/modify-amount")
    public Result<ChargingOrder> modifyAmount(@RequestBody Map<String, Object> request) {
        return Result.success(chargingService.modifyAmount(
                pickString(request, "carId", "car_Id", "car_id"),
                pickBigDecimal(request, "amount", "Amount", "targetKwh", "Request_Amount")
        ));
    }

    @PostMapping("/modify-mode")
    public Result<ChargingOrder> modifyMode(@RequestBody Map<String, Object> request) {
        return Result.success(chargingService.modifyMode(
                pickString(request, "carId", "car_Id", "car_id"),
                parseRequestMode(pickString(request, "mode", "Mode", "requestMode", "Request_Mode"))
        ));
    }

    private RequestMode parseRequestMode(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("requestMode is required");
        }

        String normalized = value.trim().toUpperCase();
        if ("1".equals(normalized) || "FAST".equals(normalized)) {
            return RequestMode.FAST;
        }
        if ("2".equals(normalized) || "SLOW".equals(normalized)) {
            return RequestMode.SLOW;
        }
        throw new BusinessException("requestMode must be FAST, SLOW, 1 or 2");
    }

    private String pickString(Map<String, Object> request, String... keys) {
        for (String key : keys) {
            Object value = request.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private BigDecimal pickBigDecimal(Map<String, Object> request, String... keys) {
        String value = pickString(request, keys);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new BusinessException("amount must be a number");
        }
    }
}

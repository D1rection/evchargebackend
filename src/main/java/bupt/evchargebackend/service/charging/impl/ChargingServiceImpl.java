package bupt.evchargebackend.service.charging.impl;

import bupt.evchargebackend.common.exception.BusinessException;
import bupt.evchargebackend.common.exception.ErrorCode;
import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.charging.ChargingRequest;
import bupt.evchargebackend.dto.charging.ChargingResponse;
import bupt.evchargebackend.dto.charging.ChargingStartRequest;
import bupt.evchargebackend.dto.charging.ChargingStartResponse;
import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.charging.ChargingSession;
import bupt.evchargebackend.entity.charging.enums.OrderStatus;
import bupt.evchargebackend.entity.charging.enums.RequestMode;
import bupt.evchargebackend.entity.charging.enums.SessionStatus;
import bupt.evchargebackend.entity.pile.ChargingPile;
import bupt.evchargebackend.entity.pile.enums.PileType;
import bupt.evchargebackend.entity.pile.enums.PowerState;
import bupt.evchargebackend.entity.pile.enums.WorkingState;
import bupt.evchargebackend.entity.pricing.BillingRatePeriod;
import bupt.evchargebackend.mapper.charging.ChargingOrderMapper;
import bupt.evchargebackend.mapper.charging.ChargingSessionMapper;
import bupt.evchargebackend.mapper.pile.ChargingPileMapper;
import bupt.evchargebackend.mapper.pricing.BillingRatePeriodMapper;
import bupt.evchargebackend.mapper.user.CarMapper;
import bupt.evchargebackend.service.charging.ChargingService;
import bupt.evchargebackend.common.time.TimeProvider;
import bupt.evchargebackend.service.schedule.SchedulingEngine;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ChargingServiceImpl implements ChargingService {

    private final ChargingOrderMapper chargingOrderMapper;
    private final CarMapper carMapper;
    private final SchedulingEngine engine;
    private final ChargingPileMapper chargingPileMapper;
    private final ChargingSessionMapper chargingSessionMapper;
    private final BillingRatePeriodMapper billingRatePeriodMapper;
    private final TimeProvider timeProvider;

    public ChargingServiceImpl(ChargingOrderMapper chargingOrderMapper, CarMapper carMapper,
                               SchedulingEngine engine, ChargingPileMapper chargingPileMapper,
                               ChargingSessionMapper chargingSessionMapper,
                               BillingRatePeriodMapper billingRatePeriodMapper,
                               TimeProvider timeProvider) {
        this.chargingOrderMapper = chargingOrderMapper;
        this.carMapper = carMapper;
        this.engine = engine;
        this.chargingPileMapper = chargingPileMapper;
        this.chargingSessionMapper = chargingSessionMapper;
        this.billingRatePeriodMapper = billingRatePeriodMapper;
        this.timeProvider = timeProvider;
    }

    @Override
    public Result<ChargingResponse> submit(ChargingRequest request) {
        // 1. 校验车辆是否存在
        String carId = request.resolveCarId();
        if (!hasText(carId)) {
            return Result.error(400, "carId is required");
        }
        var car = carMapper.selectById(carId);
        if (car == null) {
            return Result.error(404, "车辆不存在");
        }

        // 2. 校验充电量
        BigDecimal amount = request.resolveAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return Result.error(400, "充电量必须大于 0");
        }
        if (car.getBatteryCapacityKwh() != null
                && amount.compareTo(car.getBatteryCapacityKwh()) > 0) {
            return Result.error(400, "充电量不能超过电池容量 " + car.getBatteryCapacityKwh() + " kWh");
        }

        // 3. 解析充电模式
        String modeStr = request.resolveMode();
        if (!hasText(modeStr)) {
            return Result.error(400, "充电模式不能为空");
        }
        RequestMode mode;
        String normalized = modeStr.trim().toUpperCase();
        try {
            mode = RequestMode.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return Result.error(400, "充电模式必须为 FAST 或 SLOW");
        }

        // 4. 校验车辆无进行中的订单
        Long activeCount = chargingOrderMapper.selectCount(
                new QueryWrapper<ChargingOrder>()
                        .eq("car_id", carId)
                        .in("order_status", List.of("WAITING", "CALLED", "CHARGING"))
        );
        if (activeCount != null && activeCount > 0) {
            return Result.error(400, "该车辆已有进行中的订单");
        }

        // 5. 计算预估费用和用时
        PileType pileType = mode == RequestMode.FAST ? PileType.FAST : PileType.SLOW;
        BigDecimal power = BigDecimal.valueOf(pileType == PileType.FAST ? 30 : 10);
        BigDecimal rate = lookupRate(pileType);
        BigDecimal estimatedFee = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        int estimatedMinutes = amount.divide(power, 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(60)).intValue();

        // 6. 创建订单（持久化预估费用和用时）
        ChargingOrder order = new ChargingOrder();
        order.setOrderId(UUID.randomUUID().toString());
        order.setOrderNo("ORD-" + System.currentTimeMillis());
        order.setCarId(carId);
        order.setRequestMode(mode);
        order.setTargetKwh(amount);
        order.setEstimatedFee(estimatedFee);
        order.setEstimatedMinutes(estimatedMinutes);
        order.setOrderStatus(OrderStatus.WAITING);
        chargingOrderMapper.insert(order);

        // 7. 调度：有故障时订单进入等候区（故障队列优先分发），否则选最优桩
        String selectedPileId = null;
        if (engine.hasAnyFault()) {
            engine.enqueueWait(order);
        } else {
            List<ChargingPile> piles = chargingPileMapper.selectList(
                    new QueryWrapper<ChargingPile>()
                            .eq("pile_type", pileType)
                            .ne("working_state", "FAULT")
            );
            if (!piles.isEmpty()) {
                piles.sort(Comparator.comparingLong(this::totalActiveMinutes));
                ChargingPile best = piles.getFirst();
                if (engine.addToPileQueue(best.getPileId(), order)) {
                    order.setOrderStatus(OrderStatus.CALLED);
                    chargingOrderMapper.updateById(order);
                    selectedPileId = best.getPileId();
                }
            }
            if (selectedPileId == null) {
                engine.enqueueWait(order);
            }
        }

        // 8. 组装响应
        OrderStatus status = order.getOrderStatus();
        String carPosition = status == OrderStatus.CHARGING ? "充电区" : "等候区";
        String carState = status.name().toLowerCase();
        int queueNum;
        if (selectedPileId != null) {
            queueNum = engine.pileQueueSize(selectedPileId);
        } else {
            queueNum = engine.waitQueueSize(pileType);
        }
        String requestTime = timeProvider.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        ChargingResponse resp = new ChargingResponse();
        resp.setCarPosition(carPosition);
        resp.setCarState(carState);
        resp.setQueueNum(queueNum);
        resp.setRequestTime(requestTime);
        resp.setEstimatedFee(estimatedFee);
        resp.setEstimatedMinutes(estimatedMinutes);

        return Result.success(resp);
    }

    @Override
    public Result<ChargingStartResponse> start(ChargingStartRequest request) {
        // 1. 校验 carId
        String carId = request.getCarId();
        if (!hasText(carId)) {
            return Result.error(400, "车辆 ID 不能为空");
        }

        // 2. 校验车辆是否存在
        var car = carMapper.selectById(carId);
        if (car == null) {
            return Result.error(404, "车辆不存在");
        }

        // 3. 查找 CALLED 状态的订单
        ChargingOrder order = chargingOrderMapper.selectOne(
                new QueryWrapper<ChargingOrder>()
                        .eq("car_id", carId)
                        .eq("order_status", "CALLED")
                        .orderByDesc("created_at")
                        .last("LIMIT 1")
        );
        if (order == null) {
            return Result.error(400, "该车辆没有待充电的订单");
        }

        // 4. 查找充电桩
        String pileId = request.getChargePileNum();
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) {
            return Result.error(404, "充电桩不存在");
        }

        // 5. 校验桩状态（电源开启 + 可用）
        if (pile.getPowerState() != PowerState.ON) {
            return Result.error(400, "充电桩电源未开启");
        }
        if (pile.getWorkingState() != WorkingState.AVAILABLE) {
            return Result.error(400, "充电桩当前不可用");
        }

        // 6. 校验桩队列头部是该订单
        ChargingOrder head = engine.peekPileQueue(pileId);
        if (head == null || !head.getOrderId().equals(order.getOrderId())) {
            return Result.error(400, "该订单不在充电桩队列首位");
        }

        // 7. 开始充电
        startCharging(pile, order);

        ChargingStartResponse resp = new ChargingStartResponse();
        resp.setResult(1);
        return Result.success(resp);
    }

    @Override
    public ChargingOrder modifyAmount(String carId, BigDecimal amount) {
        if (!hasText(carId)) {
            throw new BusinessException("carId is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("amount must be greater than 0");
        }

        ChargingOrder order = requireModifiableOrder(carId);
        order.setTargetKwh(amount);
        chargingOrderMapper.updateById(order);
        return order;
    }

    @Override
    public ChargingOrder modifyMode(String carId, RequestMode requestMode) {
        if (!hasText(carId)) {
            throw new BusinessException("carId is required");
        }
        if (requestMode == null) {
            throw new BusinessException("requestMode is required");
        }

        ChargingOrder order = requireModifiableOrder(carId);
        order.setRequestMode(requestMode);
        chargingOrderMapper.updateById(order);
        return order;
    }

    private ChargingOrder requireModifiableOrder(String carId) {
        ChargingOrder order = chargingOrderMapper.selectOne(
                new QueryWrapper<ChargingOrder>()
                        .eq("car_id", carId)
                        .in("order_status", List.of("WAITING", "CALLED"))
                        .orderByDesc("created_at")
                        .last("LIMIT 1")
        );
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return order;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 查询当前时段的电价 + 服务费（元/kWh）。 */
    private BigDecimal lookupRate(PileType pileType) {
        LocalTime now = timeProvider.now().toLocalTime();
        List<BillingRatePeriod> periods = billingRatePeriodMapper.selectList(
                new QueryWrapper<BillingRatePeriod>().eq("pile_type", pileType)
        );
        for (var p : periods) {
            LocalTime start = LocalTime.parse(p.getStartTime());
            LocalTime end = LocalTime.parse(p.getEndTime());
            if (start.compareTo(end) <= 0) {
                if (!now.isBefore(start) && now.isBefore(end)) {
                    return p.getElectricityPrice().add(p.getServicePrice());
                }
            } else {
                // 跨天时段（如 22:00 - 06:00）
                if (!now.isBefore(start) || now.isBefore(end)) {
                    return p.getElectricityPrice().add(p.getServicePrice());
                }
            }
        }
        // 兜底费率
        return new BigDecimal("1.0");
    }

    /** 计算指定桩的总剩余充电时长（当前会话 + 排队订单，分钟）。 */
    private long totalActiveMinutes(ChargingPile pile) {
        BigDecimal power = BigDecimal.valueOf(pile.getPowerKw());
        long total = 0;
        // 当前充电会话的剩余时间
        if (pile.getCurrentSessionId() != null) {
            ChargingSession session = chargingSessionMapper.selectById(pile.getCurrentSessionId());
            if (session != null && session.getSessionStatus() == SessionStatus.CHARGING) {
                BigDecimal remaining = session.getTargetKwh().subtract(session.getChargedKwh());
                total += remaining.divide(power, 2, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(60)).longValue();
            }
        }
        // 桩队列中等待的订单
        for (var order : engine.getPileQueue(pile.getPileId())) {
            total += order.getTargetKwh().divide(power, 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(60)).longValue();
        }
        return total;
    }

    /** 开始充电：创建充电会话，更新桩状态和订单状态。 */
    private void startCharging(ChargingPile pile, ChargingOrder order) {
        pile.setWorkingState(WorkingState.CHARGING);
        chargingPileMapper.updateById(pile);

        ChargingSession session = new ChargingSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setOrderId(order.getOrderId());
        session.setCarId(order.getCarId());
        session.setPileId(pile.getPileId());
        session.setTargetKwh(order.getTargetKwh());
        session.setChargedKwh(BigDecimal.ZERO);
        session.setSessionStatus(SessionStatus.CHARGING);
        session.setStartTime(timeProvider.now());
        pile.setCurrentSessionId(session.getSessionId());
        chargingSessionMapper.insert(session);

        order.setOrderStatus(OrderStatus.CHARGING);
        chargingOrderMapper.updateById(order);
        engine.setCharging(pile.getPileId(), order);
    }
}

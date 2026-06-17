package bupt.evchargebackend.service.charging.impl;

import bupt.evchargebackend.common.exception.BusinessException;
import bupt.evchargebackend.common.exception.ErrorCode;
import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.charging.ChargingRequest;
import bupt.evchargebackend.dto.charging.ChargingResponse;
import bupt.evchargebackend.dto.charging.ChargingCancelRequest;
import bupt.evchargebackend.dto.charging.ChargingEndRequest;
import bupt.evchargebackend.dto.charging.ChargingEndResponse;
import bupt.evchargebackend.dto.charging.ChargingStateResponse;
import bupt.evchargebackend.dto.charging.ModifyResponse;
import bupt.evchargebackend.dto.charging.QueueStatusResponse;
import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.charging.ChargingSession;
import bupt.evchargebackend.entity.queue.QueueEntry;
import bupt.evchargebackend.entity.charging.enums.OrderStatus;
import bupt.evchargebackend.entity.charging.enums.RequestMode;
import bupt.evchargebackend.entity.charging.enums.SessionStatus;
import bupt.evchargebackend.entity.pile.ChargingPile;
import bupt.evchargebackend.entity.pile.enums.PileType;
import bupt.evchargebackend.entity.pile.enums.WorkingState;
import bupt.evchargebackend.entity.bill.Bill;
import bupt.evchargebackend.entity.bill.enums.PaymentStatus;
import bupt.evchargebackend.entity.pricing.BillingRatePeriod;
import bupt.evchargebackend.mapper.charging.ChargingOrderMapper;
import bupt.evchargebackend.mapper.bill.BillMapper;
import bupt.evchargebackend.mapper.charging.ChargingSessionMapper;
import bupt.evchargebackend.mapper.pile.ChargingPileMapper;
import bupt.evchargebackend.mapper.pricing.BillingRatePeriodMapper;
import bupt.evchargebackend.mapper.queue.QueueEntryMapper;
import bupt.evchargebackend.mapper.user.CarMapper;
import bupt.evchargebackend.service.charging.ChargingService;
import bupt.evchargebackend.common.time.TimeProvider;
import bupt.evchargebackend.service.schedule.SchedulingEngine;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class ChargingServiceImpl implements ChargingService {

    private static final Logger log = LoggerFactory.getLogger(ChargingServiceImpl.class);

    private final ChargingOrderMapper chargingOrderMapper;
    private final CarMapper carMapper;
    private final SchedulingEngine engine;
    private final ChargingPileMapper chargingPileMapper;
    private final ChargingSessionMapper chargingSessionMapper;
    private final BillingRatePeriodMapper billingRatePeriodMapper;
    private final QueueEntryMapper queueEntryMapper;
    private final BillMapper billMapper;
    private final ScheduledExecutorService scheduler;
    private final TimeProvider timeProvider;

    public ChargingServiceImpl(ChargingOrderMapper chargingOrderMapper, CarMapper carMapper,
                               SchedulingEngine engine, ChargingPileMapper chargingPileMapper,
                               ChargingSessionMapper chargingSessionMapper,
                               BillingRatePeriodMapper billingRatePeriodMapper,
                               QueueEntryMapper queueEntryMapper,
                               BillMapper billMapper,
                               ScheduledExecutorService scheduler,
                               TimeProvider timeProvider) {
        this.chargingOrderMapper = chargingOrderMapper;
        this.carMapper = carMapper;
        this.engine = engine;
        this.chargingPileMapper = chargingPileMapper;
        this.chargingSessionMapper = chargingSessionMapper;
        this.billingRatePeriodMapper = billingRatePeriodMapper;
        this.queueEntryMapper = queueEntryMapper;
        this.billMapper = billMapper;
        this.scheduler = scheduler;
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

        // 5. 创建订单（先设 WAITING，调度后更新预估值）
        PileType pileType = mode == RequestMode.FAST ? PileType.FAST : PileType.SLOW;
        ChargingOrder order = new ChargingOrder();
        order.setOrderId(UUID.randomUUID().toString());
        order.setOrderNo("ORD-" + System.currentTimeMillis());
        order.setCarId(carId);
        order.setRequestMode(mode);
        order.setTargetKwh(amount);
        order.setOrderStatus(OrderStatus.WAITING);
        chargingOrderMapper.insert(order);

        // 6. 调度
        if (!dispatchOrder(order, pileType)) {
            return Result.error(400, "等候区已满，无法提交申请");
        }

        // 7. 组装响应
        OrderStatus status = order.getOrderStatus();
        String carPosition = status == OrderStatus.WAITING ? "等候区" : "充电区";
        String carState = status.name().toLowerCase();
        int queueNum;
        if (status == OrderStatus.CHARGING) {
            queueNum = 0;
        } else if (order.getPileId() != null) {
            queueNum = engine.pileQueueSize(order.getPileId());
        } else {
            queueNum = engine.waitQueueSize(pileType);
        }
        String requestTime = timeProvider.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        ChargingResponse resp = new ChargingResponse();
        resp.setCarPosition(carPosition);
        resp.setCarState(carState);
        resp.setQueueNum(queueNum);
        resp.setRequestTime(requestTime);
        resp.setEstimatedFee(order.getEstimatedFee());
        resp.setEstimatedMinutes(order.getEstimatedMinutes());

        return Result.success(resp);
    }

    @Override
    public Result<Map<String, Object>> getPeriodByTime(String time) {
        int minutes = parseMinutes(time);

        List<BillingRatePeriod> periods = billingRatePeriodMapper.selectList(null);
        Map<String, Object> result = new LinkedHashMap<>();

        for (BillingRatePeriod p : periods) {
            int start = parseMinutes(p.getStartTime());
            int end = parseMinutes(p.getEndTime());
            boolean matched;
            if (start <= end) {
                matched = start <= minutes && minutes < end;
            } else {
                matched = minutes >= start || minutes < end;
            }
            if (matched) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("periodName", p.getPeriodName().name());
                item.put("startTime", p.getStartTime());
                item.put("endTime", p.getEndTime());
                item.put("electricityPrice", p.getElectricityPrice().doubleValue());
                item.put("servicePrice", p.getServicePrice().doubleValue());
                result.put(p.getPileType().name(), item);
            }
        }

        if (result.isEmpty()) {
            throw new BusinessException(400, "未找到匹配的计费时段");
        }
        return Result.success(result);
    }

    @Override
    public Result<QueueStatusResponse> queueStatus(String carId) {
        // 1. 校验 carId
        if (!hasText(carId)) {
            return Result.error(400, "车辆 ID 不能为空");
        }

        // 2. 查找最近订单
        ChargingOrder order = chargingOrderMapper.selectOne(
                new QueryWrapper<ChargingOrder>()
                        .eq("car_id", carId)
                        .orderByDesc("created_at")
                        .last("LIMIT 1")
        );
        if (order == null) {
            return Result.error(404, "充电请求不存在");
        }

        // 3. 按状态计算位置
        OrderStatus status = order.getOrderStatus();
        String carState;
        int queueNum;
        int before;
        String assignedPileId = null;
        switch (status) {
            case WAITING -> {
                carState = "waiting";
                PileType pt = order.getRequestMode() == RequestMode.FAST ? PileType.FAST : PileType.SLOW;
                int pos = engine.waitPosition(pt, order.getOrderId());
                if (pos < 0) pos = 0;
                before = pos;
                queueNum = pos + 1;
            }
            case CALLED -> {
                carState = "called";
                assignedPileId = order.getPileId();
                int pos = engine.pilePosition(assignedPileId, order.getOrderId());
                if (pos < 0) pos = 0;
                before = pos;
                queueNum = pos + 1;
            }
            case CHARGING -> {
                carState = "charging";
                assignedPileId = order.getPileId();
                queueNum = 0;
                before = 0;
            }
            default -> {
                carState = "done";
                queueNum = 0;
                before = 0;
            }
        }

        // 4. 组装响应
        QueueStatusResponse resp = new QueueStatusResponse();
        resp.setCarState(carState);
        resp.setQueueNum(queueNum);
        resp.setCarNumberBeforePosition(before);
        resp.setRequestTime(order.getCreatedAt() != null
                ? order.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : null);
        resp.setAssignedPileNum(assignedPileId);
        return Result.success(resp);
    }

    @Override
    public Result<ChargingStateResponse> chargingState(String carId) {
        // 1. 校验 carId
        if (!hasText(carId)) {
            return Result.error(400, "车辆 ID 不能为空");
        }

        // 2. 查找当前充电会话（没有则返回 none）
        ChargingSession session = chargingSessionMapper.selectOne(
                new QueryWrapper<ChargingSession>()
                        .eq("car_id", carId)
                        .eq("session_status", SessionStatus.CHARGING)
                        .orderByDesc("created_at")
                        .last("LIMIT 1")
        );
        ChargingStateResponse resp = new ChargingStateResponse();
        resp.setCarId(carId);
        if (session == null) {
            resp.setStatus("none");
            return Result.success(resp);
        }

        // 3. 基础字段
        resp.setStatus("charging");
        resp.setOrderId(session.getOrderId());
        resp.setPileNum(session.getPileId());
        resp.setStartTime(session.getStartTime() != null
                ? session.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : null);

        // 4. 计算充电量和时长
        ChargingPile pile = chargingPileMapper.selectById(session.getPileId());
        BigDecimal power = BigDecimal.valueOf(pile != null ? pile.getPowerKw() : 0);

        long elapsedSeconds = Duration.between(session.getStartTime(), timeProvider.now()).getSeconds();
        if (elapsedSeconds < 0) elapsedSeconds = 0;
        BigDecimal target = session.getTargetKwh() != null ? session.getTargetKwh() : BigDecimal.ZERO;
        BigDecimal estimatedKwh = power.multiply(BigDecimal.valueOf(elapsedSeconds))
                .divide(BigDecimal.valueOf(3600), 10, RoundingMode.HALF_UP);
        BigDecimal chargedKwh = estimatedKwh.min(target).setScale(2, RoundingMode.HALF_UP);

        resp.setCurrentAmount(chargedKwh);
        resp.setCurrentDuration(formatDuration(elapsedSeconds));

        // 5. 计算费用和电价
        PileType pileType = pile != null ? pile.getPileType() : null;

        List<BillingRatePeriod> periods = billingRatePeriodMapper.selectList(
                new QueryWrapper<BillingRatePeriod>().eq("pile_type", pileType)
        );

        if (session.getStartTime() != null && pileType != null) {
            FeeResult feeResult = calculateFees(power, target, session.getStartTime(), timeProvider.now(), periods);
            resp.setCurrentAmount(feeResult.totalKwh.setScale(2, RoundingMode.HALF_UP));
            resp.setCurrentChargeFee(feeResult.chargeFee.setScale(2, RoundingMode.HALF_UP));
            resp.setCurrentServiceFee(feeResult.serviceFee.setScale(2, RoundingMode.HALF_UP));
            resp.setTotalCurrentFee(feeResult.chargeFee.add(feeResult.serviceFee).setScale(2, RoundingMode.HALF_UP));

            LocalTime nowTime = timeProvider.now().toLocalTime();
            resp.setCurrentPeriodPrice(currentElectricityPrice(periods, nowTime));
            resp.setNextPeriodPrice(nextElectricityPrice(periods, nowTime));
        }

        return Result.success(resp);
    }

    @Override
    public Result<ChargingEndResponse> end(ChargingEndRequest request) {
        // 1. 校验参数
        String carId = request.getCarId();
        String pileId = request.getChargingPileNum();
        if (!hasText(carId)) {
            return Result.error(400, "车辆 ID 不能为空");
        }
        if (!hasText(pileId)) {
            return Result.error(400, "充电桩 ID 不能为空");
        }

        // 2. 查找充电会话
        ChargingSession session = chargingSessionMapper.selectOne(
                new QueryWrapper<ChargingSession>()
                        .eq("car_id", carId)
                        .eq("pile_id", pileId)
                        .eq("session_status", SessionStatus.CHARGING)
                        .orderByDesc("created_at")
                        .last("LIMIT 1")
        );
        if (session == null) {
            return Result.error(400, "未找到充电中的会话");
        }

        // 3. 查找订单和桩
        ChargingOrder order = chargingOrderMapper.selectById(session.getOrderId());
        if (order == null) {
            return Result.error(404, "订单不存在");
        }
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) {
            return Result.error(404, "充电桩不存在");
        }

        // 4. 计算费用
        PileType pileType = pile.getPileType();
        BigDecimal power = BigDecimal.valueOf(pile.getPowerKw());
        BigDecimal target = session.getTargetKwh() != null ? session.getTargetKwh() : BigDecimal.ZERO;
        LocalDateTime endTime = timeProvider.now();

        List<BillingRatePeriod> periods = billingRatePeriodMapper.selectList(
                new QueryWrapper<BillingRatePeriod>().eq("pile_type", pileType)
        );

        FeeResult feeResult = calculateFees(power, target, session.getStartTime(), endTime, periods);
        long chargeMinutes = Duration.between(session.getStartTime(), endTime).toMinutes();

        // 5. 创建账单
        Bill bill = new Bill();
        bill.setBillId(UUID.randomUUID().toString());
        bill.setBillNo("BILL-" + System.currentTimeMillis());
        bill.setOrderId(order.getOrderId());
        bill.setSessionId(session.getSessionId());
        bill.setCarId(carId);
        bill.setPileId(pileId);
        bill.setStartTime(session.getStartTime());
        bill.setEndTime(endTime);
        bill.setChargedKwh(feeResult.totalKwh.setScale(2, RoundingMode.HALF_UP));
        bill.setChargeMinutes((int) chargeMinutes);
        bill.setElectricityFee(feeResult.chargeFee.setScale(2, RoundingMode.HALF_UP));
        bill.setServiceFee(feeResult.serviceFee.setScale(2, RoundingMode.HALF_UP));
        bill.setTotalFee(feeResult.chargeFee.add(feeResult.serviceFee).setScale(2, RoundingMode.HALF_UP));
        bill.setPaymentStatus(PaymentStatus.UNPAID);
        billMapper.insert(bill);

        // 6. 更新会话
        session.setSessionStatus(SessionStatus.FINISHED);
        session.setEndTime(endTime);
        session.setChargedKwh(feeResult.totalKwh);
        chargingSessionMapper.updateById(session);

        // 7. 更新订单
        order.setOrderStatus(OrderStatus.FINISHED);
        chargingOrderMapper.updateById(order);

        // 8. 删 queue_entry + 引擎释放
        QueueEntry qe = queueEntryMapper.selectOne(
                new QueryWrapper<QueueEntry>()
                        .eq("queue_type", "PILE")
                        .eq("queue_key", pileId)
                        .orderByAsc("id")
                        .last("LIMIT 1")
        );
        if (qe != null) {
            queueEntryMapper.deleteById(qe.getId());
        }
        engine.onPileReleased(pileId, pileType);

        // 9. 更新桩（使 totalActiveMinutes 能正确计算）
        pile.setWorkingState(WorkingState.AVAILABLE);
        pile.setCurrentSessionId(null);
        chargingPileMapper.updateById(pile);

        // 10. 补位 + 自动开始
        tryFillFromWaiting(pileId, pileType);
        tryAutoStartNextCar(pileId);

        ChargingEndResponse resp = new ChargingEndResponse();
        resp.setResult(1);
        return Result.success(resp);
    }

    @Override
    public Result<ChargingEndResponse> cancel(ChargingCancelRequest request) {
        // 1. 校验 carId
        String carId = request.getCarId();
        if (!hasText(carId)) {
            return Result.error(400, "车辆 ID 不能为空");
        }

        // 2. 查找 WAITING 或 CALLED 订单
        ChargingOrder order = chargingOrderMapper.selectOne(
                new QueryWrapper<ChargingOrder>()
                        .eq("car_id", carId)
                        .in("order_status", List.of("WAITING", "CALLED"))
                        .orderByDesc("created_at")
                        .last("LIMIT 1")
        );
        if (order == null) {
            return Result.error(404, "没有可取消的充电请求");
        }

        // 3. 从队列移除 + 删 queue_entry
        String pileId = order.getPileId();
        String orderId = order.getOrderId();

        if (order.getOrderStatus() == OrderStatus.WAITING) {
            engine.removeFromWait(carId);
        } else {
            engine.removeFromAllPileQueues(carId);
        }
        queueEntryMapper.delete(
                new QueryWrapper<QueueEntry>().eq("order_id", orderId)
        );

        // 4. 更新订单状态
        order.setOrderStatus(OrderStatus.CANCELLED);
        chargingOrderMapper.updateById(order);

        // 5. CALLED 取消后尝试补位
        if (pileId != null) {
            ChargingPile pile = chargingPileMapper.selectById(pileId);
            if (pile != null) {
                tryFillFromWaiting(pileId, pile.getPileType());
                tryAutoStartNextCar(pileId);
            }
        }

        ChargingEndResponse resp = new ChargingEndResponse();
        resp.setResult(1);
        return Result.success(resp);
    }

    /**
     * 修改充电量：更新目标电量并重算预估价和时长。队列位置不变。
     */
    @Override
    public Result<ModifyResponse> modifyAmount(String carId, BigDecimal amount) {
        if (!hasText(carId)) {
            throw new BusinessException("carId is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("amount must be greater than 0");
        }

        ChargingOrder order = requireModifiableOrder(carId);
        order.setTargetKwh(amount);

        // 重算预估
        PileType pileType = order.getRequestMode() == RequestMode.FAST ? PileType.FAST : PileType.SLOW;
        BigDecimal power = BigDecimal.valueOf(pileType == PileType.FAST ? 30 : 10);
        int chargeMinutes = amount.divide(power, 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(60)).intValue();
        int estimatedMinutes;
        if (order.getPileId() != null) {
            ChargingPile pile = chargingPileMapper.selectById(order.getPileId());
            estimatedMinutes = pile != null ? (int) totalActiveMinutes(pile) : chargeMinutes;
        } else {
            estimatedMinutes = chargeMinutes;
        }
        if (estimatedMinutes < chargeMinutes) estimatedMinutes = chargeMinutes;

        List<BillingRatePeriod> periods = billingRatePeriodMapper.selectList(
                new QueryWrapper<BillingRatePeriod>().eq("pile_type", pileType)
        );
        LocalDateTime estimateEnd = timeProvider.now().plusMinutes(estimatedMinutes);
        FeeResult feeResult = calculateFees(power, amount, timeProvider.now(), estimateEnd, periods);
        order.setEstimatedFee(feeResult.chargeFee.add(feeResult.serviceFee).setScale(2, RoundingMode.HALF_UP));
        order.setEstimatedMinutes(estimatedMinutes);
        chargingOrderMapper.updateById(order);

        ModifyResponse resp = new ModifyResponse();
        resp.setResult(1);
        return Result.success(resp);
    }

    /**
     * 修改充电模式：移出当前队列 → 切换模式 → 重新调度 → 原桩补位。
     */
    @Override
    public Result<ModifyResponse> modifyMode(String carId, RequestMode requestMode) {
        if (!hasText(carId)) {
            throw new BusinessException("carId is required");
        }
        if (requestMode == null) {
            throw new BusinessException("requestMode is required");
        }

        ChargingOrder order = requireModifiableOrder(carId);
        if (order.getRequestMode() == requestMode) {
            ModifyResponse resp = new ModifyResponse();
            resp.setResult(1);
            return Result.success(resp);
        }

        // 从当前队列移除
        String oldPileId = order.getPileId();
        if (order.getOrderStatus() == OrderStatus.WAITING) {
            engine.removeFromWait(carId);
        } else {
            engine.removeFromAllPileQueues(carId);
        }
        queueEntryMapper.delete(new QueryWrapper<QueueEntry>().eq("order_id", order.getOrderId()));

        // 更新模式并重新调度
        order.setRequestMode(requestMode);
        PileType pileType = requestMode == RequestMode.FAST ? PileType.FAST : PileType.SLOW;
        dispatchOrder(order, pileType);

        // 如果之前排在桩队列，尝试让原桩从等候区补位
        if (oldPileId != null) {
            ChargingPile oldPile = chargingPileMapper.selectById(oldPileId);
            if (oldPile != null) {
                tryFillFromWaiting(oldPileId, oldPile.getPileType());
                tryAutoStartNextCar(oldPileId);
            }
        }

        ModifyResponse resp = new ModifyResponse();
        resp.setResult(1);
        return Result.success(resp);
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
    /** 将 HH:mm 格式转为当天分钟数。 */
    private static int parseMinutes(String time) {
        if (time == null || !time.matches("\\d{1,2}:\\d{2}")) {
            throw new BusinessException(400, "时间格式无效，应为 HH:mm");
        }
        String[] parts = time.split(":");
        int h = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]);
        if (h == 24 && m == 0) {
            return 24 * 60;
        }
        if (h < 0 || h > 23 || m < 0 || m > 59) {
            throw new BusinessException(400, "时间值无效，小时 0~23，分钟 0~59");
        }
        return h * 60 + m;
    }

    /** 计算指定桩的总剩余充电时长（当前会话 + 排队订单，分钟）。 */
    private long totalActiveMinutes(ChargingPile pile) {
        BigDecimal power = BigDecimal.valueOf(pile.getPowerKw());
        long total = 0;
        String sessionOrderId = null;
        // 当前充电会话的剩余时间
        if (pile.getCurrentSessionId() != null) {
            ChargingSession session = chargingSessionMapper.selectById(pile.getCurrentSessionId());
            if (session != null && session.getSessionStatus() == SessionStatus.CHARGING) {
                sessionOrderId = session.getOrderId();
                BigDecimal remaining = session.getTargetKwh().subtract(session.getChargedKwh());
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                    total += remaining.divide(power, 2, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(60)).longValue();
                }
            }
        }
        // position 0：如果指向的不是当前充电车，则是下一辆待充电车
        ChargingOrder pos0 = engine.peekPileQueue(pile.getPileId());
        if (pos0 != null && !pos0.getOrderId().equals(sessionOrderId)) {
            total += pos0.getTargetKwh().divide(power, 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(60)).longValue();
        }
        // position 1+
        for (var order : engine.getPileQueue(pile.getPileId())) {
            total += order.getTargetKwh().divide(power, 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(60)).longValue();
        }
        return total;
    }

    /** 开始充电：创建充电会话，更新桩状态和订单状态。 */
    private void startCharging(ChargingPile pile, ChargingOrder order) {
        log.warn("startCharging: pileId={}, orderId={}, carId={}", pile.getPileId(), order.getOrderId(), order.getCarId());
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
        chargingPileMapper.updateById(pile);
        chargingSessionMapper.insert(session);

        // 预约充满自动结束（模拟模式下由 SimulationService 推进）
        if (!timeProvider.isSimulating()) {
            BigDecimal powerKw = BigDecimal.valueOf(pile.getPowerKw());
            long delayMs = order.getTargetKwh()
                    .divide(powerKw, 10, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(3600_000))
                    .longValue();
            String sid = session.getSessionId();
            scheduler.schedule(() -> autoFinish(sid), delayMs, TimeUnit.MILLISECONDS);
        }

        order.setOrderStatus(OrderStatus.CHARGING);
        chargingOrderMapper.updateById(order);
        engine.setCharging(pile.getPileId(), order);
    }

    private static final int MAX_WAITING_CAPACITY = 10;

    /** 调度：查同类型桩 → 有空位入桩队列（空闲则 auto-start）→ 否则入等候区（满则丢弃），同时计算预估。 */
    /** @return true 表示已派入桩队列或等候区，false 表示等候区满被丢弃 */
    private boolean dispatchOrder(ChargingOrder order, PileType pileType) {
        String waitQueueKey = pileType == PileType.FAST ? "FAST" : "SLOW";
        String selectedPileId = null;
        ChargingPile bestPile = null;

        if (engine.hasAnyFault()) {
            if (engine.totalWaitSize() < MAX_WAITING_CAPACITY) {
                engine.enqueueWait(order);
                insertQueueEntry("WAIT", waitQueueKey, order.getOrderId());
            } else {
                return false;
            }
        } else {
            List<ChargingPile> piles = chargingPileMapper.selectList(
                    new QueryWrapper<ChargingPile>()
                            .eq("pile_type", pileType)
                            .in("working_state", "AVAILABLE", "CHARGING")
            );
            if (!piles.isEmpty()) {
                piles.sort(Comparator.comparingLong(this::totalActiveMinutes));
                bestPile = piles.getFirst();
                if (engine.addToPileQueue(bestPile.getPileId(), order)) {
                    order.setPileId(bestPile.getPileId());
                    selectedPileId = bestPile.getPileId();
                    insertQueueEntry("PILE", selectedPileId, order.getOrderId());
                    if (bestPile.getWorkingState() == WorkingState.AVAILABLE
                            && bestPile.getCurrentSessionId() == null) {
                        startCharging(bestPile, order);
                    } else {
                        order.setOrderStatus(OrderStatus.CALLED);
                    }
                }
            }
            if (selectedPileId == null) {
                if (engine.totalWaitSize() < MAX_WAITING_CAPACITY) {
                    engine.enqueueWait(order);
                    insertQueueEntry("WAIT", waitQueueKey, order.getOrderId());
                } else {
                    return false;
                }
            }
        }

        // 计算预估
        BigDecimal amount = order.getTargetKwh();
        BigDecimal power = BigDecimal.valueOf(pileType == PileType.FAST ? 30 : 10);
        int chargeMinutes = amount.divide(power, 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(60)).intValue();
        int estimatedMinutes = selectedPileId != null
                ? (int) totalActiveMinutes(bestPile) : chargeMinutes;
        if (estimatedMinutes < chargeMinutes) estimatedMinutes = chargeMinutes;

        List<BillingRatePeriod> periods = billingRatePeriodMapper.selectList(
                new QueryWrapper<BillingRatePeriod>().eq("pile_type", pileType)
        );
        LocalDateTime estimateEnd = timeProvider.now().plusMinutes(estimatedMinutes);
        FeeResult feeResult = calculateFees(power, amount, timeProvider.now(), estimateEnd, periods);
        order.setEstimatedFee(feeResult.chargeFee.add(feeResult.serviceFee).setScale(2, RoundingMode.HALF_UP));
        order.setEstimatedMinutes(estimatedMinutes);
        chargingOrderMapper.updateById(order);
        return true;
    }

    /** 桩释放后补位：同类型有故障桩时从故障桩队列取车（优先级高于等候区）。 */
    private void tryFillFromWaiting(String pileId, PileType pileType) {
        // 有同类型故障桩 → 从故障桩队列取一车（位置 0 优先）
        ChargingPile faultedPile = chargingPileMapper.selectList(
                new QueryWrapper<ChargingPile>()
                        .eq("pile_type", pileType)
                        .eq("working_state", "FAULT")
                        .last("LIMIT 1")
        ).stream().findFirst().orElse(null);
        // 从故障桩 deque 逐个取车，派到最优桩，派不出去就停
        boolean faultEmpty = false;
        while (faultedPile != null) {
            ChargingOrder order = engine.pollFromPileQueueHead(faultedPile.getPileId());
            if (order == null) { faultEmpty = true; break; }
            // 取走的是充电车 → 清空桩的 session 引用，避免恢复后 tryAutoStartNextCar 跳过
            faultedPile.setCurrentSessionId(null);
            chargingPileMapper.updateById(faultedPile);
            String targetPile = dispatchToBestPile(order, pileType);
            if (targetPile == null) {
                engine.setCharging(faultedPile.getPileId(), order);
                break;
            }
            resumeInterruptedSession(order, targetPile);
        }
        // 有故障且（本类型故障队列非空 或 跨类型故障存在）→ 不调度等候区
        if (faultedPile != null && !faultEmpty) return;
        if (engine.hasAnyFault()) return;

        String waitKey = pileType == PileType.FAST ? "FAST" : "SLOW";
        QueueEntry qe = queueEntryMapper.selectOne(
                new QueryWrapper<QueueEntry>()
                        .eq("queue_type", "WAIT")
                        .eq("queue_key", waitKey)
                        .orderByAsc("id")
                        .last("LIMIT 1")
        );
        if (qe == null) return;

        ChargingOrder order = chargingOrderMapper.selectById(qe.getOrderId());
        if (order == null) return;
        if (order.getOrderStatus() != OrderStatus.WAITING) {
            queueEntryMapper.deleteById(qe.getId());
            return;
        }

        String selected = dispatchToBestPile(order, pileType);
        if (selected != null) {
            engine.removeFromWait(order.getCarId());
            queueEntryMapper.deleteById(qe.getId());
        }
    }

    /** 尝试将订单派到最优桩（同类型中 totalActiveMinutes 最小者），仅设 CALLED，不触发 start（由 tryAutoStartNextCar 处理）。 */
    private String dispatchToBestPile(ChargingOrder order, PileType pileType) {
        ChargingPile best = chargingPileMapper.selectList(
                new QueryWrapper<ChargingPile>()
                        .eq("pile_type", pileType)
                        .in("working_state", "AVAILABLE", "CHARGING")
        ).stream().min(Comparator.comparingLong(this::totalActiveMinutes)).orElse(null);
        if (best == null) return null;
        if (!engine.addToPileQueue(best.getPileId(), order)) return null;

        order.setOrderStatus(OrderStatus.CALLED);
        order.setPileId(best.getPileId());
        chargingOrderMapper.updateById(order);
        insertQueueEntry("PILE", best.getPileId(), order.getOrderId());
        return best.getPileId();
    }

    /** 将中断的 session 恢复为 CHARGING（pileId 不变，等 startCharging 新建 session）。 */
    private void resumeInterruptedSession(ChargingOrder order, String newPileId) {
        // 故障队列的车被派到新桩的队列时，中断 session 暂时保持中断。
        // 等车在目标桩上通过 startCharging 创建新 session 后，原中断 session 交由 autoFinish 处理。
    }

    /** 桩空闲后自动开始下一辆车：桩 AVAILABLE 且队列 position 0 有 CALLED 订单则开始充电。 */
    private void tryAutoStartNextCar(String pileId) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) return;
        if (pile.getWorkingState() != WorkingState.AVAILABLE) {
            log.warn("tryAutoStartNextCar {} skip: workingState={}", pileId, pile.getWorkingState());
            return;
        }
        if (pile.getCurrentSessionId() != null) return;
        ChargingOrder next = engine.peekPileQueue(pileId);
        if (next != null && next.getOrderStatus() == OrderStatus.CALLED) {
            log.warn("tryAutoStartNextCar {} start: carId={}, orderId={}", pileId, next.getCarId(), next.getOrderId());
            startCharging(pile, next);
        }
    }

    @Override
    public void onPileRecovered(String pileId) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) return;
        tryFillFromWaiting(pileId, pile.getPileType());
        tryAutoStartNextCar(pileId);
    }

    @Override
    public void autoFinish(String sessionId) {
        ChargingSession session = chargingSessionMapper.selectById(sessionId);
        if (session == null || session.getSessionStatus() != SessionStatus.CHARGING) return;

        ChargingOrder order = chargingOrderMapper.selectById(session.getOrderId());
        ChargingPile pile = chargingPileMapper.selectById(session.getPileId());
        if (order == null || pile == null) return;

        PileType pileType = pile.getPileType();
        BigDecimal power = BigDecimal.valueOf(pile.getPowerKw());
        BigDecimal target = session.getTargetKwh() != null ? session.getTargetKwh() : BigDecimal.ZERO;
        LocalDateTime endTime = timeProvider.now();

        List<BillingRatePeriod> periods = billingRatePeriodMapper.selectList(
                new QueryWrapper<BillingRatePeriod>().eq("pile_type", pileType)
        );
        FeeResult feeResult = calculateFees(power, target, session.getStartTime(), endTime, periods);
        long chargeMinutes = Duration.between(session.getStartTime(), endTime).toMinutes();

        // 清理队列
        QueueEntry qe = queueEntryMapper.selectOne(
                new QueryWrapper<QueueEntry>()
                        .eq("queue_type", "PILE")
                        .eq("queue_key", pile.getPileId())
                        .orderByAsc("id")
                        .last("LIMIT 1")
        );
        if (qe != null) queueEntryMapper.deleteById(qe.getId());
        engine.onPileReleased(pile.getPileId(), pileType);

        // 先更新桩状态，使 totalActiveMinutes 能正确计算
        pile.setWorkingState(WorkingState.AVAILABLE);
        pile.setCurrentSessionId(null);
        chargingPileMapper.updateById(pile);

        // 补位（故障队列优先） + 自动开始
        tryFillFromWaiting(pile.getPileId(), pileType);
        tryAutoStartNextCar(pile.getPileId());

        Bill bill = new Bill();
        bill.setBillId(UUID.randomUUID().toString());
        bill.setBillNo("BILL-" + System.currentTimeMillis());
        bill.setOrderId(order.getOrderId());
        bill.setSessionId(session.getSessionId());
        bill.setCarId(session.getCarId());
        bill.setPileId(pile.getPileId());
        bill.setStartTime(session.getStartTime());
        bill.setEndTime(endTime);
        bill.setChargedKwh(feeResult.totalKwh.setScale(2, RoundingMode.HALF_UP));
        bill.setChargeMinutes((int) chargeMinutes);
        bill.setElectricityFee(feeResult.chargeFee.setScale(2, RoundingMode.HALF_UP));
        bill.setServiceFee(feeResult.serviceFee.setScale(2, RoundingMode.HALF_UP));
        bill.setTotalFee(feeResult.chargeFee.add(feeResult.serviceFee).setScale(2, RoundingMode.HALF_UP));
        bill.setPaymentStatus(PaymentStatus.UNPAID);
        billMapper.insert(bill);

        session.setSessionStatus(SessionStatus.FINISHED);
        session.setEndTime(endTime);
        session.setChargedKwh(feeResult.totalKwh);
        chargingSessionMapper.updateById(session);

        order.setOrderStatus(OrderStatus.FINISHED);
        chargingOrderMapper.updateById(order);
    }

    private void insertQueueEntry(String queueType, String queueKey, String orderId) {
        QueueEntry entry = new QueueEntry();
        entry.setQueueType(queueType);
        entry.setQueueKey(queueKey);
        entry.setOrderId(orderId);
        queueEntryMapper.insert(entry);
    }

    private static FeeResult calculateFees(BigDecimal power, BigDecimal target,
                                            LocalDateTime start, LocalDateTime end,
                                            List<BillingRatePeriod> periods) {
        BigDecimal totalKwh = BigDecimal.ZERO;
        BigDecimal chargeFee = BigDecimal.ZERO;
        BigDecimal serviceFee = BigDecimal.ZERO;

        LocalDateTime cursor = start;
        while (cursor.isBefore(end) && totalKwh.compareTo(target) < 0) {
            int currentMinute = cursor.getHour() * 60 + cursor.getMinute();

            BillingRatePeriod period = findPeriod(periods, currentMinute);
            if (period == null) break;

            int ps = parseMinutes(period.getStartTime());
            int pe = parseMinutes(period.getEndTime());

            LocalDateTime periodEnd;
            if (ps <= pe) {
                periodEnd = cursor.toLocalDate().atStartOfDay().plusMinutes(pe);
            } else {
                int cm = cursor.getHour() * 60 + cursor.getMinute();
                if (cm >= ps) {
                    periodEnd = cursor.toLocalDate().atStartOfDay().plusDays(1).plusMinutes(pe);
                } else {
                    periodEnd = cursor.toLocalDate().atStartOfDay().plusMinutes(pe);
                }
            }

            LocalDateTime sliceEnd = periodEnd.isBefore(end) ? periodEnd : end;
            if (sliceEnd.equals(cursor)) break;

            long sliceSeconds = Duration.between(cursor, sliceEnd).getSeconds();

            BigDecimal kwh = power.multiply(BigDecimal.valueOf(sliceSeconds))
                    .divide(BigDecimal.valueOf(3600), 10, RoundingMode.HALF_UP);

            BigDecimal remaining = target.subtract(totalKwh);
            if (kwh.compareTo(remaining) > 0) kwh = remaining;

            chargeFee = chargeFee.add(kwh.multiply(period.getElectricityPrice()));
            serviceFee = serviceFee.add(kwh.multiply(period.getServicePrice()));
            totalKwh = totalKwh.add(kwh);

            cursor = sliceEnd;
        }

        return new FeeResult(totalKwh, chargeFee, serviceFee);
    }

    private static BillingRatePeriod findPeriod(List<BillingRatePeriod> periods, int minuteOfDay) {
        for (BillingRatePeriod p : periods) {
            int start = parseMinutes(p.getStartTime());
            int end = parseMinutes(p.getEndTime());
            if (start <= end) {
                if (minuteOfDay >= start && minuteOfDay < end) return p;
            } else {
                if (minuteOfDay >= start || minuteOfDay < end) return p;
            }
        }
        return null;
    }

    private static BigDecimal currentElectricityPrice(List<BillingRatePeriod> periods, LocalTime time) {
        int m = time.getHour() * 60 + time.getMinute();
        BillingRatePeriod p = findPeriod(periods, m);
        return p != null ? p.getElectricityPrice() : BigDecimal.ONE;
    }

    private static BigDecimal nextElectricityPrice(List<BillingRatePeriod> periods, LocalTime time) {
        int m = time.getHour() * 60 + time.getMinute();
        BillingRatePeriod best = null;
        int bestDist = Integer.MAX_VALUE;
        for (BillingRatePeriod p : periods) {
            int start = parseMinutes(p.getStartTime());
            int dist;
            if (start > m) {
                dist = start - m;
            } else {
                dist = start + 1440 - m;
            }
            if (dist < bestDist) {
                bestDist = dist;
                best = p;
            }
        }
        return best != null ? best.getElectricityPrice() : BigDecimal.ONE;
    }

    private record FeeResult(BigDecimal totalKwh, BigDecimal chargeFee, BigDecimal serviceFee) {}

    private static String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}

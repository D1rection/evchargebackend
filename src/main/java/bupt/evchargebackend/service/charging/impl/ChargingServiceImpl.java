package bupt.evchargebackend.service.charging.impl;

import bupt.evchargebackend.common.exception.BusinessException;
import bupt.evchargebackend.common.exception.ErrorCode;
import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.charging.ChargingRequest;
import bupt.evchargebackend.dto.charging.ChargingResponse;
import bupt.evchargebackend.dto.charging.ChargingStartRequest;
import bupt.evchargebackend.dto.charging.ChargingStartResponse;
import bupt.evchargebackend.dto.charging.ChargingStateResponse;
import bupt.evchargebackend.dto.charging.QueueStatusResponse;
import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.charging.ChargingSession;
import bupt.evchargebackend.entity.queue.QueueEntry;
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
import bupt.evchargebackend.mapper.queue.QueueEntryMapper;
import bupt.evchargebackend.mapper.user.CarMapper;
import bupt.evchargebackend.service.charging.ChargingService;
import bupt.evchargebackend.common.time.TimeProvider;
import bupt.evchargebackend.service.schedule.SchedulingEngine;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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

@Service
public class ChargingServiceImpl implements ChargingService {

    private final ChargingOrderMapper chargingOrderMapper;
    private final CarMapper carMapper;
    private final SchedulingEngine engine;
    private final ChargingPileMapper chargingPileMapper;
    private final ChargingSessionMapper chargingSessionMapper;
    private final BillingRatePeriodMapper billingRatePeriodMapper;
    private final QueueEntryMapper queueEntryMapper;
    private final TimeProvider timeProvider;

    public ChargingServiceImpl(ChargingOrderMapper chargingOrderMapper, CarMapper carMapper,
                               SchedulingEngine engine, ChargingPileMapper chargingPileMapper,
                               ChargingSessionMapper chargingSessionMapper,
                               BillingRatePeriodMapper billingRatePeriodMapper,
                               QueueEntryMapper queueEntryMapper,
                               TimeProvider timeProvider) {
        this.chargingOrderMapper = chargingOrderMapper;
        this.carMapper = carMapper;
        this.engine = engine;
        this.chargingPileMapper = chargingPileMapper;
        this.chargingSessionMapper = chargingSessionMapper;
        this.billingRatePeriodMapper = billingRatePeriodMapper;
        this.queueEntryMapper = queueEntryMapper;
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
        String waitQueueKey = pileType == PileType.FAST ? "FAST" : "SLOW";
        if (engine.hasAnyFault()) {
            engine.enqueueWait(order);
            insertQueueEntry("WAIT", waitQueueKey, order.getOrderId());
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
                    order.setPileId(best.getPileId());
                    chargingOrderMapper.updateById(order);
                    insertQueueEntry("PILE", best.getPileId(), order.getOrderId());
                    selectedPileId = best.getPileId();
                }
            }
            if (selectedPileId == null) {
                engine.enqueueWait(order);
                insertQueueEntry("WAIT", waitQueueKey, order.getOrderId());
            }
        }

        // 8. 组装响应
        OrderStatus status = order.getOrderStatus();
        String carPosition = status == OrderStatus.WAITING ? "等候区" : "充电区";
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
    public Result<List<Map<String, Object>>> getPeriodByTime(String time) {
        int minutes = parseMinutes(time);

        List<BillingRatePeriod> periods = billingRatePeriodMapper.selectList(null);
        List<Map<String, Object>> result = new java.util.ArrayList<>();
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
                result.add(item);
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

        // 2. 查找最近充电会话
        ChargingSession session = chargingSessionMapper.selectOne(
                new QueryWrapper<ChargingSession>()
                        .eq("car_id", carId)
                        .orderByDesc("created_at")
                        .last("LIMIT 1")
        );
        ChargingStateResponse resp = new ChargingStateResponse();
        resp.setCarId(carId);
        if (session == null) {
            resp.setStatus("none");
            return Result.success(resp);
        }

        // 3. 映射状态 + 基础字段
        String status = switch (session.getSessionStatus()) {
            case CHARGING -> "charging";
            case FINISHED -> "completed";
            case INTERRUPTED -> "interrupted";
        };
        resp.setStatus(status);
        resp.setOrderId(session.getOrderId());
        resp.setPileNum(session.getPileId());
        resp.setStartTime(session.getStartTime() != null
                ? session.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : null);

        // 4. 计算充电量和时长
        BigDecimal chargedKwh = null;
        String duration = null;

        if (session.getSessionStatus() == SessionStatus.CHARGING) {
            ChargingPile pile = chargingPileMapper.selectById(session.getPileId());
            BigDecimal power = BigDecimal.valueOf(pile != null ? pile.getPowerKw() : 0);

            long elapsedSeconds = Duration.between(session.getStartTime(), timeProvider.now()).getSeconds();
            if (elapsedSeconds < 0) elapsedSeconds = 0;
            long elapsedMinutes = elapsedSeconds / 60;

            BigDecimal maxCharge = session.getTargetKwh() != null ? session.getTargetKwh() : BigDecimal.ZERO;
            BigDecimal estimatedKwh = power.multiply(BigDecimal.valueOf(elapsedMinutes))
                    .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP);
            chargedKwh = estimatedKwh.min(maxCharge).setScale(2, RoundingMode.HALF_UP);
            duration = formatDuration(elapsedSeconds);
        } else {
            chargedKwh = session.getChargedKwh();
            if (session.getStartTime() != null && session.getEndTime() != null) {
                long seconds = Duration.between(session.getStartTime(), session.getEndTime()).getSeconds();
                duration = formatDuration(Math.max(0, seconds));
            }
        }

        resp.setCurrentAmount(chargedKwh != null ? chargedKwh : BigDecimal.ZERO);
        resp.setCurrentDuration(duration);

        // 5. 计算费用和电价
        ChargingPile pile = chargingPileMapper.selectById(session.getPileId());
        PileType pileType = pile != null ? pile.getPileType() : null;
        BigDecimal power = pile != null ? BigDecimal.valueOf(pile.getPowerKw()) : BigDecimal.ZERO;
        BigDecimal target = session.getTargetKwh() != null ? session.getTargetKwh() : BigDecimal.ZERO;

        LocalDateTime calcStart = session.getStartTime();
        LocalDateTime calcEnd = session.getSessionStatus() == SessionStatus.CHARGING
                ? timeProvider.now() : session.getEndTime();

        List<BillingRatePeriod> periods = billingRatePeriodMapper.selectList(
                new QueryWrapper<BillingRatePeriod>().eq("pile_type", pileType)
        );

        if (calcStart != null && calcEnd != null && pileType != null) {
            FeeResult feeResult = calculateFees(power, target, calcStart, calcEnd, periods);
            resp.setCurrentAmount(feeResult.totalKwh.setScale(2, RoundingMode.HALF_UP));
            resp.setCurrentChargeFee(feeResult.chargeFee.setScale(2, RoundingMode.HALF_UP));
            resp.setCurrentServiceFee(new BigDecimal("5.00"));
            resp.setTotalCurrentFee(feeResult.chargeFee.add(new BigDecimal("5.00")).setScale(2, RoundingMode.HALF_UP));

            if (session.getSessionStatus() == SessionStatus.CHARGING) {
                LocalTime nowTime = timeProvider.now().toLocalTime();
                resp.setCurrentPeriodPrice(currentElectricityPrice(periods, nowTime));
                resp.setNextPeriodPrice(nextElectricityPrice(periods, nowTime));
            }
        }

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

            long sliceMinutes = Duration.between(cursor, sliceEnd).toMinutes();

            BigDecimal kwh = power.multiply(BigDecimal.valueOf(sliceMinutes))
                    .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP);

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

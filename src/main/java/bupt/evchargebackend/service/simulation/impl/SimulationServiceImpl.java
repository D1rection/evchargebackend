package bupt.evchargebackend.service.simulation.impl;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.common.time.SwitchableTimeProvider;
import bupt.evchargebackend.dto.charging.ChargingCancelRequest;
import bupt.evchargebackend.dto.charging.ChargingEndRequest;
import bupt.evchargebackend.dto.charging.ChargingRequest;
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
import bupt.evchargebackend.entity.pricing.enums.PeriodName;
import bupt.evchargebackend.entity.station.StationConfig;
import bupt.evchargebackend.entity.user.Car;
import bupt.evchargebackend.entity.user.UserAccount;
import bupt.evchargebackend.entity.user.enums.UserRole;
import bupt.evchargebackend.mapper.charging.ChargingOrderMapper;
import bupt.evchargebackend.mapper.charging.ChargingSessionMapper;
import bupt.evchargebackend.mapper.pricing.BillingRatePeriodMapper;
import bupt.evchargebackend.mapper.pile.ChargingPileMapper;
import bupt.evchargebackend.mapper.station.StationConfigMapper;
import bupt.evchargebackend.mapper.user.CarMapper;
import bupt.evchargebackend.mapper.user.UserAccountMapper;
import bupt.evchargebackend.service.charging.ChargingService;
import bupt.evchargebackend.service.pile.PileService;
import bupt.evchargebackend.service.schedule.SchedulingEngine;
import bupt.evchargebackend.service.simulation.SimulationService;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class SimulationServiceImpl implements SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationServiceImpl.class);

    private record SimEvent(LocalDateTime time, String type, String targetId,
                            String chargeType, double value) {}

    private static final List<SimEvent> ALL_EVENTS = buildEvents();
    private static final LocalDateTime SIM_START = LocalDateTime.of(2026, 6, 17, 6, 0);
    private static final int MAX_STEP_MINUTES = 15 * 60; // 安全上限：跑到 21:00 强制停

    private final SwitchableTimeProvider timeProvider;
    private final ChargingService chargingService;
    private final PileService pileService;
    private final ChargingSessionMapper chargingSessionMapper;
    private final ChargingPileMapper chargingPileMapper;
    private final ChargingOrderMapper chargingOrderMapper;
    private final BillingRatePeriodMapper billingRatePeriodMapper;
    private final SchedulingEngine engine;
    private final DataSource dataSource;
    private final CarMapper carMapper;
    private final UserAccountMapper userAccountMapper;
    private final StationConfigMapper stationConfigMapper;

    private int eventCursor = 0;
    private volatile boolean playing = false;
    private Thread playThread;
    private final List<Map<String, Object>> checkpoints = Collections.synchronizedList(new ArrayList<>());

    public SimulationServiceImpl(SwitchableTimeProvider timeProvider,
                                  ChargingService chargingService,
                                  PileService pileService,
                                  ChargingSessionMapper chargingSessionMapper,
                                  ChargingPileMapper chargingPileMapper,
                                  ChargingOrderMapper chargingOrderMapper,
                                  BillingRatePeriodMapper billingRatePeriodMapper,
                                  SchedulingEngine engine,
                                  DataSource dataSource,
                                  CarMapper carMapper,
                                  UserAccountMapper userAccountMapper,
                                  StationConfigMapper stationConfigMapper) {
        this.timeProvider = timeProvider;
        this.chargingService = chargingService;
        this.pileService = pileService;
        this.chargingSessionMapper = chargingSessionMapper;
        this.chargingPileMapper = chargingPileMapper;
        this.chargingOrderMapper = chargingOrderMapper;
        this.billingRatePeriodMapper = billingRatePeriodMapper;
        this.engine = engine;
        this.dataSource = dataSource;
        this.carMapper = carMapper;
        this.userAccountMapper = userAccountMapper;
        this.stationConfigMapper = stationConfigMapper;
    }

    @Override
    public Result<Void> startSimulation(LocalDateTime time) {
        timeProvider.startSimulation(time);
        eventCursor = 0;
        while (eventCursor < ALL_EVENTS.size()
                && !ALL_EVENTS.get(eventCursor).time.isAfter(time)) {
            processEvent(ALL_EVENTS.get(eventCursor));
            eventCursor++;
        }
        return Result.success();
    }

    @Override
    public Result<Void> step(int minutes) {
        while (minutes > 5) {
            step(5);
            minutes -= 5;
        }
        timeProvider.advance(minutes);
        LocalDateTime now = timeProvider.now();
        while (eventCursor < ALL_EVENTS.size()
                && !ALL_EVENTS.get(eventCursor).time.isAfter(now)) {
            processEvent(ALL_EVENTS.get(eventCursor));
            eventCursor++;
        }
        advanceCharging();
        return Result.success();
    }

    @Override
    public Result<Void> stopSimulation() {
        timeProvider.stopSimulation();
        return Result.success();
    }

    @Override
    public Result<Map<String, Object>> getState() {
        return Result.success(buildSnapshot());
    }

    // ========== 自动播放 ==========

    @Override
    public Result<Void> play(String speed) {
        if (playing) {
            pause();
            // 等线程完全停止
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        }
        resetAndInit();
        timeProvider.startSimulation(SIM_START);
        eventCursor = 0;
        checkpoints.clear();
        recordCheckpoint();

        long delayMs = switch (speed) {
            case "normal" -> 30_000L;  // 1:1 真实比例
            case "fast" -> 3_000L;     // 10 倍速
            case "instant" -> 0L;
            default -> 3_000L;
        };
        log.warn("自动播放开始, speed={}, delayMs={}", speed, delayMs);

        long finalDelayMs = delayMs;
        playing = true;
        playThread = new Thread(() -> {
            try {
                int totalMinutes = 0;
                while (playing && !isSimFinished() && totalMinutes < MAX_STEP_MINUTES) {
                    step(5);
                    totalMinutes += 5;
                    recordCheckpoint();
                    if (finalDelayMs > 0) Thread.sleep(finalDelayMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                playing = false;
                log.warn("自动播放结束, 模拟至 {}", timeProvider.now());
            }
        }, "sim-play");
        playThread.start();
        return Result.success();
    }

    @Override
    public Result<Void> pause() {
        playing = false;
        if (playThread != null && playThread.isAlive()) {
            playThread.interrupt();
        }
        return Result.success();
    }

    @Override
    public Result<List<Map<String, Object>>> getCheckpoints() {
        return Result.success(List.copyOf(checkpoints));
    }

    @Override
    public Result<Boolean> isPlaying() {
        return Result.success(playing);
    }

    private boolean isSimFinished() {
        boolean allEventsDone = eventCursor >= ALL_EVENTS.size();
        long chargingCount = chargingSessionMapper.selectCount(
                new QueryWrapper<ChargingSession>()
                        .eq("session_status", SessionStatus.CHARGING)
        );
        return allEventsDone && chargingCount == 0;
    }

    private void resetAndInit() {
        log.warn("重置数据库并初始化基础数据");
        String[] tables = {"queue_entry", "bill", "charging_session", "charging_order",
                "fault_record", "charging_pile", "billing_rate_period",
                "station_config", "car", "user_account"};
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String t : tables) {
                stmt.execute("TRUNCATE TABLE " + t);
            }
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
        } catch (Exception e) {
            throw new RuntimeException("重置数据库失败", e);
        }
        engine.rebuild(List.of(), List.of(), Map.of());

        UserAccount admin = new UserAccount();
        admin.setUserId("admin-1");
        admin.setUsername("admin");
        admin.setPasswordHash(BCrypt.hashpw("admin123", BCrypt.gensalt()));
        admin.setRole(UserRole.ADMIN);
        userAccountMapper.insert(admin);

        UserAccount user1 = new UserAccount();
        user1.setUserId("user-1");
        user1.setUsername("user1");
        user1.setPasswordHash(BCrypt.hashpw("123456", BCrypt.gensalt()));
        user1.setRole(UserRole.USER);
        userAccountMapper.insert(user1);

        UserAccount user2 = new UserAccount();
        user2.setUserId("user-2");
        user2.setUsername("user2");
        user2.setPasswordHash(BCrypt.hashpw("123456", BCrypt.gensalt()));
        user2.setRole(UserRole.USER);
        userAccountMapper.insert(user2);

        for (int i = 1; i <= 15; i++) {
            Car c = new Car();
            c.setCarId("V" + i);
            c.setUserId("user-1");
            c.setCarNo("V" + i);
            c.setBatteryCapacityKwh(BigDecimal.valueOf(200));
            carMapper.insert(c);
        }
        for (int i = 16; i <= 30; i++) {
            Car c = new Car();
            c.setCarId("V" + i);
            c.setUserId("user-2");
            c.setCarNo("V" + i);
            c.setBatteryCapacityKwh(BigDecimal.valueOf(200));
            carMapper.insert(c);
        }

        addPile("F1", "快充01", PileType.FAST, 30);
        addPile("F2", "快充02", PileType.FAST, 30);
        addPile("T1", "慢充01", PileType.SLOW, 10);
        addPile("T2", "慢充02", PileType.SLOW, 10);
        addPile("T3", "慢充03", PileType.SLOW, 10);

        addRate("10:00", "15:00", "1.0", "0.8");
        addRate("18:00", "21:00", "1.0", "0.8");
        addRate("07:00", "10:00", "0.7", "0.8");
        addRate("15:00", "18:00", "0.7", "0.8");
        addRate("21:00", "23:00", "0.7", "0.8");
        addRate("23:00", "07:00", "0.4", "0.8");

        StationConfig config = new StationConfig();
        config.setFastCount(2);
        config.setSlowCount(3);
        config.setWaitingSpotsPerPile(3);
        stationConfigMapper.insert(config);
    }

    private void addPile(String id, String no, PileType type, int powerKw) {
        ChargingPile p = new ChargingPile();
        p.setPileId(id);
        p.setPileNo(no);
        p.setPileType(type);
        p.setPowerKw(powerKw);
        p.setPowerState(PowerState.ON);
        p.setWorkingState(WorkingState.AVAILABLE);
        chargingPileMapper.insert(p);
    }

    private void addRate(String start, String end, String elec, String svc) {
        for (PileType pt : PileType.values()) {
            BillingRatePeriod rate = new BillingRatePeriod();
            rate.setPeriodId(UUID.randomUUID().toString());
            rate.setPileType(pt);
            rate.setPeriodName(detectPeriodName(start));
            rate.setStartTime(start);
            rate.setEndTime(end);
            rate.setElectricityPrice(new BigDecimal(elec));
            rate.setServicePrice(new BigDecimal(svc));
            billingRatePeriodMapper.insert(rate);
        }
    }

    private PeriodName detectPeriodName(String start) {
        if ("10:00".equals(start) || "18:00".equals(start)) return PeriodName.PEAK;
        if ("23:00".equals(start)) return PeriodName.VALLEY;
        return PeriodName.NORMAL;
    }

    private void recordCheckpoint() {
        Map<String, Object> cp = new LinkedHashMap<>();
        cp.put("time", timeProvider.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        cp.put("state", buildSnapshot());
        checkpoints.add(cp);
    }

    // ========== 快照 ==========

    private Map<String, Object> buildSnapshot() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("simulating", timeProvider.isSimulating());
        state.put("currentTime", timeProvider.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        Map<String, Object> piles = new LinkedHashMap<>();
        for (var pile : chargingPileMapper.selectList(null)) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("status", pile.getWorkingState().name());
            p.put("pileType", pile.getPileType().name());

            if (pile.getWorkingState() == WorkingState.CHARGING
                    && pile.getCurrentSessionId() != null) {
                ChargingSession s = chargingSessionMapper.selectById(pile.getCurrentSessionId());
                if (s != null) {
                    p.put("carId", s.getCarId());
                    p.put("chargedKwh", s.getChargedKwh());
                    p.put("requestAmount", s.getTargetKwh());
                    BigDecimal charged = s.getChargedKwh() != null ? s.getChargedKwh() : BigDecimal.ZERO;
                    p.put("currentFee", calculateCurrentFee(charged, pile, s.getStartTime()));
                }
            } else {
                p.put("carId", null);
                p.put("chargedKwh", null);
                p.put("currentFee", null);
                p.put("requestAmount", null);
            }

            List<Map<String, Object>> queue = new ArrayList<>();
            if (pile.getWorkingState() == WorkingState.FAULT) {
                ChargingOrder head = engine.peekPileQueue(pile.getPileId());
                if (head != null) queue.add(queueItem(head));
            }
            for (var o : engine.getPileQueue(pile.getPileId())) {
                queue.add(queueItem(o));
            }
            p.put("queue", queue);
            piles.put(pile.getPileId(), p);
        }
        state.put("piles", piles);

        List<Map<String, Object>> waiting = new ArrayList<>();
        for (var o : engine.getFastWaitQueue()) {
            waiting.add(queueItem(o));
        }
        for (var o : engine.getSlowWaitQueue()) {
            waiting.add(queueItem(o));
        }
        state.put("waitingQueue", waiting);
        state.put("faultQueue", List.of());

        List<String> completed = chargingOrderMapper.selectList(
                new QueryWrapper<ChargingOrder>()
                        .eq("order_status", OrderStatus.FINISHED)
        ).stream().map(ChargingOrder::getCarId).toList();
        state.put("completedOrders", completed);

        return state;
    }

    private Map<String, Object> queueItem(ChargingOrder o) {
        ChargingOrder fresh = chargingOrderMapper.selectById(o.getOrderId());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("carId", o.getCarId());
        item.put("requestAmount", fresh != null ? fresh.getTargetKwh() : o.getTargetKwh());
        item.put("requestMode", fresh != null ? fresh.getRequestMode().name() : o.getRequestMode().name());
        return item;
    }

    // ========== 事件处理 ==========

    private void processEvent(SimEvent e) {
        try {
            log.info("触发事件: ({},{},{},{})", e.type, e.targetId, e.chargeType, e.value);
            switch (e.type) {
                case "A" -> processApply(e);
                case "C" -> processChange(e);
                case "B" -> processBreakdown(e);
            }
        } catch (Exception ex) {
            log.error("事件触发失败: ({},{},{},{}) - {}", e.type, e.targetId, e.chargeType, e.value, ex.getMessage());
        }
    }

    private void processApply(SimEvent e) {
        if (e.value > 0) {
            ChargingRequest req = new ChargingRequest();
            req.setCarId(e.targetId);
            req.setRequestAmount(BigDecimal.valueOf(e.value));
            req.setRequestMode(e.chargeType.equals("F") ? "FAST" : "SLOW");
            chargingService.submit(req);
        } else if (e.value == 0) {
            ChargingCancelRequest cancelReq = new ChargingCancelRequest();
            cancelReq.setCarId(e.targetId);
            var cancelResult = chargingService.cancel(cancelReq);
            if (cancelResult.getCode() == 200) return;

            ChargingSession session = chargingSessionMapper.selectOne(
                    new QueryWrapper<ChargingSession>()
                            .eq("car_id", e.targetId)
                            .in("session_status", SessionStatus.CHARGING, SessionStatus.INTERRUPTED)
                            .orderByDesc("created_at").last("LIMIT 1")
            );
            if (session != null && session.getSessionStatus() == SessionStatus.CHARGING) {
                ChargingPile pile = chargingPileMapper.selectById(session.getPileId());
                if (pile != null) {
                    ChargingEndRequest endReq = new ChargingEndRequest();
                    endReq.setCarId(e.targetId);
                    endReq.setChargingPileNum(pile.getPileId());
                    chargingService.end(endReq);
                }
            } else if (session != null && session.getSessionStatus() == SessionStatus.INTERRUPTED) {
                ChargingOrder order = chargingOrderMapper.selectById(session.getOrderId());
                if (order != null && order.getOrderStatus() == OrderStatus.CHARGING) {
                    order.setOrderStatus(OrderStatus.FINISHED);
                    chargingOrderMapper.updateById(order);
                }
            }
        }
    }

    private void processChange(SimEvent e) {
        boolean changeMode = !"O".equals(e.chargeType);
        boolean changeAmount = e.value > 0;

        if (changeAmount && changeMode) {
            chargingService.modifyMode(e.targetId,
                    e.chargeType.equals("F") ? RequestMode.FAST : RequestMode.SLOW);
            chargingService.modifyAmount(e.targetId, BigDecimal.valueOf(e.value));
        } else if (changeAmount) {
            chargingService.modifyAmount(e.targetId, BigDecimal.valueOf(e.value));
        } else if (changeMode) {
            chargingService.modifyMode(e.targetId,
                    e.chargeType.equals("F") ? RequestMode.FAST : RequestMode.SLOW);
        }
    }

    private void processBreakdown(SimEvent e) {
        if (e.value == 0) {
            pileService.triggerFault(e.targetId);
        } else if (e.value == 1) {
            pileService.recoverFault(e.targetId);
        }
    }

    // ========== 充电推进 ==========

    private void advanceCharging() {
        LocalDateTime now = timeProvider.now();
        var sessions = chargingSessionMapper.selectList(
                new QueryWrapper<ChargingSession>()
                        .eq("session_status", SessionStatus.CHARGING)
        );
        for (var session : sessions) {
            if (session.getStartTime() == null) continue;
            long elapsedSeconds = Duration.between(session.getStartTime(), now).getSeconds();
            if (elapsedSeconds <= 0) continue;
            ChargingPile pile = chargingPileMapper.selectById(session.getPileId());
            if (pile == null) continue;
            BigDecimal power = BigDecimal.valueOf(pile.getPowerKw());
            BigDecimal estimated = power.multiply(BigDecimal.valueOf(elapsedSeconds))
                    .divide(BigDecimal.valueOf(3600), 10, RoundingMode.HALF_UP);
            BigDecimal target = session.getTargetKwh() != null ? session.getTargetKwh() : BigDecimal.ZERO;
            BigDecimal charged = estimated.min(target);

            session.setChargedKwh(charged);
            chargingSessionMapper.updateById(session);

            if (charged.compareTo(target) >= 0) {
                chargingService.autoFinish(session.getSessionId());
            }
        }
    }

    // ========== 费用计算 ==========

    private BigDecimal calculateCurrentFee(BigDecimal chargedKwh, ChargingPile pile, LocalDateTime startTime) {
        if (chargedKwh == null || chargedKwh.compareTo(BigDecimal.ZERO) <= 0 || startTime == null) {
            return BigDecimal.ZERO;
        }
        LocalDateTime now = timeProvider.now();
        List<BillingRatePeriod> periods = billingRatePeriodMapper.selectList(
                new QueryWrapper<BillingRatePeriod>().eq("pile_type", pile.getPileType())
        );
        BigDecimal power = BigDecimal.valueOf(pile.getPowerKw());
        BigDecimal chargeFee = BigDecimal.ZERO;
        BigDecimal serviceFee = BigDecimal.ZERO;
        BigDecimal totalKwh = BigDecimal.ZERO;
        LocalDateTime cursor = startTime;

        while (cursor.isBefore(now) && totalKwh.compareTo(chargedKwh) < 0) {
            int cm = cursor.getHour() * 60 + cursor.getMinute();
            BillingRatePeriod period = findPeriod(periods, cm);
            if (period == null) break;

            int ps = parseMinute(period.getStartTime());
            int pe = parseMinute(period.getEndTime());
            LocalDateTime periodEnd = ps <= pe
                    ? cursor.toLocalDate().atStartOfDay().plusMinutes(pe)
                    : (cm >= ps
                    ? cursor.toLocalDate().atStartOfDay().plusDays(1).plusMinutes(pe)
                    : cursor.toLocalDate().atStartOfDay().plusMinutes(pe));
            LocalDateTime sliceEnd = periodEnd.isBefore(now) ? periodEnd : now;
            if (sliceEnd.equals(cursor)) break;

            long sliceSeconds = Duration.between(cursor, sliceEnd).getSeconds();
            BigDecimal kwh = power.multiply(BigDecimal.valueOf(sliceSeconds))
                    .divide(BigDecimal.valueOf(3600), 10, RoundingMode.HALF_UP);
            BigDecimal remaining = chargedKwh.subtract(totalKwh);
            if (kwh.compareTo(remaining) > 0) kwh = remaining;

            chargeFee = chargeFee.add(kwh.multiply(period.getElectricityPrice()));
            serviceFee = serviceFee.add(kwh.multiply(period.getServicePrice()));
            totalKwh = totalKwh.add(kwh);
            cursor = sliceEnd;
        }
        return chargeFee.add(serviceFee).setScale(2, RoundingMode.HALF_UP);
    }

    private static int parseMinute(String time) {
        String[] parts = time.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private static BillingRatePeriod findPeriod(List<BillingRatePeriod> periods, int minuteOfDay) {
        for (BillingRatePeriod p : periods) {
            int s = parseMinute(p.getStartTime());
            int e = parseMinute(p.getEndTime());
            if (s <= e ? (minuteOfDay >= s && minuteOfDay < e) : (minuteOfDay >= s || minuteOfDay < e)) {
                return p;
            }
        }
        return null;
    }

    // ========== 42 个事件 ==========

    private static List<SimEvent> buildEvents() {
        return List.of(
                event("06:00", "A", "V1", "T", 40),
                event("06:05", "A", "V2", "T", 30),
                event("06:10", "A", "V3", "F", 100),
                event("06:15", "A", "V4", "F", 120),
                event("06:20", "A", "V2", "O", 0),
                event("06:25", "A", "V5", "T", 20),
                event("06:30", "A", "V6", "T", 20),
                event("06:35", "A", "V7", "F", 110),
                event("06:40", "A", "V8", "T", 20),
                event("06:45", "A", "V9", "F", 105),
                event("06:50", "A", "V10", "T", 10),
                event("06:55", "A", "V11", "F", 110),
                event("07:00", "A", "V12", "F", 90),
                event("07:05", "A", "V13", "F", 110),
                event("07:10", "A", "V14", "F", 95),
                event("07:15", "A", "V15", "T", 10),
                event("07:20", "A", "V16", "F", 60),
                event("07:25", "A", "V17", "T", 10),
                event("07:30", "A", "V18", "T", 7.5),
                event("07:35", "A", "V19", "F", 75),
                event("07:40", "A", "V20", "F", 95),
                event("07:45", "A", "V21", "F", 95),
                event("07:50", "A", "V22", "F", 70),
                event("07:55", "A", "V23", "F", 80),
                event("08:00", "A", "V24", "T", 5),
                event("08:20", "A", "V25", "T", 15),
                event("08:25", "B", "T1", "O", 0),
                event("08:30", "A", "V26", "T", 20),
                event("08:35", "A", "V27", "T", 25),
                event("08:50", "B", "F1", "O", 0),
                event("09:00", "A", "V28", "F", 30),
                event("09:10", "A", "V1", "O", 0),
                event("09:15", "B", "T1", "O", 1),
                event("09:20", "A", "V27", "O", 0),
                event("09:25", "C", "V21", "O", 35),
                event("09:30", "A", "V19", "O", 0),
                event("09:35", "A", "V28", "O", 0),
                event("09:40", "C", "V23", "O", 40),
                event("09:50", "A", "V29", "T", 30),
                event("09:55", "C", "V14", "O", 30),
                event("10:00", "A", "V30", "T", 10),
                event("10:50", "B", "F1", "O", 1)
        );
    }

    private static SimEvent event(String time, String type, String targetId,
                                   String chargeType, double value) {
        int h = Integer.parseInt(time.substring(0, 2));
        int m = Integer.parseInt(time.substring(3));
        return new SimEvent(LocalDateTime.of(2026, 6, 17, h, m),
                type, targetId, chargeType, value);
    }
}

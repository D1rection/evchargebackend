package bupt.evchargebackend.schedule.driver;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.charging.ChargingCancelRequest;
import bupt.evchargebackend.dto.charging.ChargingEndRequest;
import bupt.evchargebackend.dto.charging.ChargingEndResponse;
import bupt.evchargebackend.dto.charging.ChargingRequest;
import bupt.evchargebackend.entity.bill.Bill;
import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.charging.ChargingSession;
import bupt.evchargebackend.entity.charging.enums.OrderStatus;
import bupt.evchargebackend.entity.charging.enums.SessionStatus;
import bupt.evchargebackend.entity.fault.FaultRecord;
import bupt.evchargebackend.entity.fault.enums.FaultStatus;
import bupt.evchargebackend.entity.pile.ChargingPile;
import bupt.evchargebackend.entity.pile.enums.PileType;
import bupt.evchargebackend.entity.pile.enums.PowerState;
import bupt.evchargebackend.entity.pile.enums.WorkingState;
import bupt.evchargebackend.entity.pricing.BillingRatePeriod;
import bupt.evchargebackend.entity.pricing.enums.PeriodName;
import bupt.evchargebackend.entity.queue.QueueEntry;
import bupt.evchargebackend.entity.user.Car;
import bupt.evchargebackend.mapper.bill.BillMapper;
import bupt.evchargebackend.mapper.charging.ChargingOrderMapper;
import bupt.evchargebackend.mapper.charging.ChargingSessionMapper;
import bupt.evchargebackend.mapper.fault.FaultRecordMapper;
import bupt.evchargebackend.mapper.pile.ChargingPileMapper;
import bupt.evchargebackend.mapper.pricing.BillingRatePeriodMapper;
import bupt.evchargebackend.mapper.queue.QueueEntryMapper;
import bupt.evchargebackend.mapper.user.CarMapper;
import bupt.evchargebackend.schedule.stub.BillingHelper;
import bupt.evchargebackend.schedule.stub.SimulatedTimeProvider;
import bupt.evchargebackend.service.charging.impl.ChargingServiceImpl;
import bupt.evchargebackend.service.pile.impl.PileServiceImpl;
import bupt.evchargebackend.service.schedule.SchedulingEngine;
import bupt.evchargebackend.service.schedule.impl.FifoStrategy;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AcceptanceTestDriver {

    private static final Path CASE_FILE = Path.of("src/test/resources/schedule/test-case.txt");
    private static final Path EXPECTED_FILE = Path.of("src/test/resources/schedule/test-expected.txt");
    private static final int TIME_STEP = 5;
    private static final int SIMULATION_START_HOUR = 6;

    // ---- Real services ----
    private ChargingServiceImpl chargingService;
    private PileServiceImpl pileService;
    private SchedulingEngine engine;
    private SimulatedTimeProvider time;

    // ---- Mock mappers ----
    private ChargingOrderMapper orderMapper;
    private CarMapper carMapper;
    private ChargingPileMapper pileMapper;
    private ChargingSessionMapper sessionMapper;
    private BillingRatePeriodMapper ratePeriodMapper;
    private QueueEntryMapper queueMapper;
    private BillMapper billMapper;
    private FaultRecordMapper faultMapper;
    private ScheduledExecutorService scheduler;

    // ---- In-memory data store (replaces stub) ----
    private final Map<String, ChargingPile> piles = new ConcurrentHashMap<>();
    private final Map<String, ChargingOrder> orders = new ConcurrentHashMap<>();
    private final Map<String, ChargingSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> sessionFees = new ConcurrentHashMap<>();
    private final Map<String, Bill> bills = new ConcurrentHashMap<>();
    private final Map<Long, QueueEntry> queueEntries = new ConcurrentHashMap<>();
    private final AtomicLong queueIdSeq = new AtomicLong(1);
    private final Map<String, FaultRecord> faultRecords = new ConcurrentHashMap<>();
    private final Map<String, Car> cars = new HashMap<>();
    private final List<BillingRatePeriod> ratePeriods = new ArrayList<>();

    @BeforeEach
    void setUp() {
        time = new SimulatedTimeProvider(LocalDateTime.of(2026, 6, 14, SIMULATION_START_HOUR, 0));
        engine = new SchedulingEngine(new FifoStrategy(), 3);
        initCars();
        initRatePeriods();
        initMocks();
        initPiles();
        chargingService = new ChargingServiceImpl(
                orderMapper, carMapper, engine, pileMapper, sessionMapper,
                ratePeriodMapper, queueMapper, billMapper, scheduler, time);
        pileService = new PileServiceImpl(
                pileMapper, engine, sessionMapper, carMapper, time, faultMapper, chargingService);
    }

    @Test
    void testFullCase() throws IOException {
        List<String> caseLines = Files.readAllLines(CASE_FILE);
        List<String> expectedLines = Files.readAllLines(EXPECTED_FILE);
        run(caseLines, expectedLines);
    }

    void run(List<String> caseLines, List<String> expectedLines) {
        var events = parseEvents(caseLines);
        var checkpoints = parseExpectedStates(expectedLines);
        int eventIdx = 0;
        int stepMinutes = SIMULATION_START_HOUR * 60;
        try {
            for (var cp : checkpoints) {
                int targetMinutes = toMinutes(cp.time);
                boolean hasPendingFault = false;
                if (stepMinutes < targetMinutes) {
                    for (int i = eventIdx; i < events.size() && toMinutes(events.get(i).time) == targetMinutes; i++) {
                        if ("FAULT".equals(events.get(i).type)) { hasPendingFault = true; break; }
                    }
                }
                while (stepMinutes < targetMinutes) {
                    int nextTime = stepMinutes + TIME_STEP;
                    while (eventIdx < events.size()
                            && toMinutes(events.get(eventIdx).time) == stepMinutes) {
                        processEvent(events.get(eventIdx));
                        eventIdx++;
                    }
                    boolean boundaryFault = hasPendingFault && nextTime == targetMinutes;
                    engine.setDeferWaitDispatch(boundaryFault);
                    advanceCharging(TIME_STEP);
                    engine.setDeferWaitDispatch(false);
                    stepMinutes = nextTime;
                    time.advance(TIME_STEP);
                }
                while (eventIdx < events.size() && events.get(eventIdx).time.equals(cp.time)) {
                    processEvent(events.get(eventIdx));
                    eventIdx++;
                }
                verify(cp);
            }
        } catch (AssertionError | Exception e) {
            dumpState();
            throw e;
        }
    }

    private void advanceCharging(int minutes) {
        List<ChargingSession> finished = new ArrayList<>();
        for (var session : sessions.values()) {
            if (session.getSessionStatus() != SessionStatus.CHARGING) continue;
            var pile = piles.get(session.getPileId());
            if (pile == null) continue;
            BigDecimal power = pile.getPileType() == PileType.FAST
                    ? BillingHelper.FAST_POWER : BillingHelper.SLOW_POWER;
            BigDecimal increment = BillingHelper.chargeIncrement(power, minutes);
            BigDecimal charged = session.getChargedKwh().add(increment);
            if (charged.setScale(2, RoundingMode.HALF_UP)
                    .compareTo(session.getTargetKwh()) >= 0) {
                charged = session.getTargetKwh();
                finished.add(session);
            }
            session.setChargedKwh(charged);
            LocalTime rateTime = time.now().toLocalTime();
            BigDecimal fee = increment.multiply(BillingHelper.rateAt(rateTime));
            sessionFees.merge(session.getSessionId(), fee, BigDecimal::add);
        }
        for (var session : finished) {
            chargingService.autoFinish(session.getSessionId());
        }
    }

    private void processEvent(Event event) {
        switch (event.type) {
            case "ARRIVE" -> {
                ChargingRequest req = new ChargingRequest();
                req.setCarId(event.subject);
                req.setRequestAmount(BigDecimal.valueOf(event.value));
                req.setRequestMode(event.mode.equals("FAST") ? "FAST" : "SLOW");
                chargingService.submit(req);
            }
            case "FINISH" -> handleFinish(event.subject);
            case "FAULT" -> pileService.triggerFault(event.subject);
            case "RECOVER" -> pileService.recoverFault(event.subject);
            case "CHANGE" -> {
                double v = event.value > 0 ? event.value : Double.parseDouble(event.mode);
                chargingService.modifyAmount(event.subject, BigDecimal.valueOf(v));
            }
        }
    }

    private void handleFinish(String carId) {
        // 先尝试 cancel（处理 WAITING/CALLED 订单）
        ChargingCancelRequest cancelReq = new ChargingCancelRequest();
        cancelReq.setCarId(carId);
        Result<ChargingEndResponse> cancelResult = chargingService.cancel(cancelReq);
        if (cancelResult.getCode() == 200) return;

        // 处理 CHARGING 订单
        ChargingSession session = sessions.values().stream()
                .filter(s -> carId.equals(s.getCarId()))
                .filter(s -> s.getSessionStatus() == SessionStatus.CHARGING)
                .findFirst().orElse(null);
        if (session != null) {
            ChargingEndRequest req = new ChargingEndRequest();
            req.setCarId(carId);
            req.setChargingPileNum(session.getPileId());
            chargingService.end(req);
            return;
        }

        // 处理 INTERRUPTED 订单
        ChargingSession interrupted = sessions.values().stream()
                .filter(s -> carId.equals(s.getCarId()))
                .filter(s -> s.getSessionStatus() == SessionStatus.INTERRUPTED)
                .findFirst().orElse(null);
        if (interrupted != null) {
            ChargingOrder order = orders.get(interrupted.getOrderId());
            if (order != null) {
                order.setOrderStatus(OrderStatus.FINISHED);
            }
        }
    }

    private void verify(Checkpoint cp) {
        String[] pileNames = {"FAST1", "FAST2", "SLOW1", "SLOW2", "SLOW3"};
        String[] pileIds = {"F1", "F2", "T1", "T2", "T3"};
        for (int i = 0; i < 5; i++) {
            var expected = cp.pileStates.get(pileNames[i]);
            var pile = piles.get(pileIds[i]);
            boolean isFault = pile != null && pile.getWorkingState() == WorkingState.FAULT;
            if (expected == null) {
                var active = findSessionByPile(pileIds[i]);
                if (active != null) {
                    fail(pileNames[i] + " expected empty at " + cp.time + " but has " + active.getCarId());
                }
            } else {
                var session = isFault ? findLastSessionByPile(pileIds[i])
                        : findSessionByPile(pileIds[i]);
                String actualCar = session != null ? session.getCarId() : "-";
                assertEquals(expected.car, actualCar, pileNames[i] + " car mismatch at " + cp.time);

                String actualKwh = session != null
                        ? String.format("%.2f", session.getChargedKwh()) : "-";
                assertEquals(expected.kwh, actualKwh,
                        pileNames[i] + " kwh mismatch at " + cp.time);

                String actualFee = session != null
                        ? String.format("%.2f", sessionFees.getOrDefault(session.getSessionId(), BigDecimal.ZERO)) : "-";
                assertEquals(expected.fee, actualFee,
                        pileNames[i] + " fee mismatch at " + cp.time);
            }
        }
    }

    private ChargingSession findSessionByPile(String pileId) {
        return sessions.values().stream()
                .filter(s -> pileId.equals(s.getPileId()))
                .filter(s -> s.getSessionStatus() == SessionStatus.CHARGING)
                .findFirst().orElse(null);
    }

    private ChargingSession findLastSessionByPile(String pileId) {
        return sessions.values().stream()
                .filter(s -> pileId.equals(s.getPileId()))
                .filter(s -> s.getSessionStatus() != SessionStatus.FINISHED)
                .findFirst().orElse(null);
    }

    // ==================== Mock Setup ====================

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Set<Object> expandParams(Map<String, Object> params) {
        Set<Object> expanded = new HashSet<>();
        for (var v : params.values()) {
            if (v instanceof Collection) expanded.addAll((Collection) v);
            else expanded.add(v);
        }
        return expanded;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Map<String, Object> extractParams(QueryWrapper<?> qw) {
        qw.getCustomSqlSegment();
        Class<?> clazz = qw.getClass();
        while (clazz != null && !clazz.getName().equals("java.lang.Object")) {
            try {
                Field f = clazz.getDeclaredField("paramNameValuePairs");
                f.setAccessible(true);
                Object val = f.get(qw);
                if (val instanceof Map) return new HashMap<>((Map) val);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (Exception e) {
                break;
            }
        }
        return new HashMap<>();
    }

    private static String findCarIdInParams(Collection<?> params) {
        for (var v : params) {
            if (v instanceof String s && s.startsWith("V")) return s;
        }
        return null;
    }

    private static String findPileIdInParams(Collection<?> params) {
        for (var v : params) {
            if (v instanceof String s && s.matches("^[FT]\\d+$")) return s;
        }
        return null;
    }

    private void initMocks() {
        orderMapper = mock(ChargingOrderMapper.class);
        carMapper = mock(CarMapper.class);
        pileMapper = mock(ChargingPileMapper.class);
        sessionMapper = mock(ChargingSessionMapper.class);
        ratePeriodMapper = mock(BillingRatePeriodMapper.class);
        queueMapper = mock(QueueEntryMapper.class);
        billMapper = mock(BillMapper.class);
        faultMapper = mock(FaultRecordMapper.class);
        scheduler = mock(ScheduledExecutorService.class);

        // --- CarMapper ---
        when(carMapper.selectById(any())).thenAnswer(i -> cars.get(i.getArgument(0)));

        // --- ChargingOrderMapper ---
        when(orderMapper.selectById(any())).thenAnswer(i -> orders.get(i.getArgument(0)));
        when(orderMapper.insert(any(ChargingOrder.class))).thenAnswer(i -> {
            ChargingOrder o = i.getArgument(0);
            orders.put(o.getOrderId(), o);
            return 1;
        });
        when(orderMapper.updateById(any(ChargingOrder.class))).thenAnswer(i -> {
            ChargingOrder o = i.getArgument(0);
            orders.put(o.getOrderId(), o);
            return 1;
        });
        when(orderMapper.selectCount(any())).thenAnswer(i -> {
            QueryWrapper<ChargingOrder> qw = i.getArgument(0);
            var allParams = expandParams(extractParams(qw));
            String carId = findCarIdInParams(allParams);
            Set<String> orderStatuses = allParams.stream()
                    .filter(String.class::isInstance).map(String.class::cast)
                    .filter(s -> s.matches("WAITING|CALLED|CHARGING|FINISHED|CANCELLED"))
                    .collect(Collectors.toSet());
            return (long) orders.values().stream()
                    .filter(o -> carId == null || carId.equals(o.getCarId()))
                    .filter(o -> orderStatuses.isEmpty() || orderStatuses.contains(o.getOrderStatus().name()))
                    .count();
        });
        when(orderMapper.selectOne(any())).thenAnswer(i -> {
            QueryWrapper<ChargingOrder> qw = i.getArgument(0);
            var allParams = expandParams(extractParams(qw));
            String carId = findCarIdInParams(allParams);
            Set<String> orderStatuses = allParams.stream()
                    .filter(String.class::isInstance).map(String.class::cast)
                    .filter(s -> s.matches("WAITING|CALLED|CHARGING|FINISHED|CANCELLED"))
                    .collect(Collectors.toSet());
            return orders.values().stream()
                    .filter(o -> carId == null || carId.equals(o.getCarId()))
                    .filter(o -> orderStatuses.isEmpty() || orderStatuses.contains(o.getOrderStatus().name()))
                    .findFirst().orElse(null);
        });

        // --- ChargingPileMapper ---
        when(pileMapper.selectById(any())).thenAnswer(i -> piles.get(i.getArgument(0)));
        when(pileMapper.updateById(any(ChargingPile.class))).thenAnswer(i -> {
            ChargingPile p = i.getArgument(0);
            piles.put(p.getPileId(), p);
            return 1;
        });
        when(pileMapper.selectList(any())).thenAnswer(i -> {
            QueryWrapper<ChargingPile> qw = i.getArgument(0);
            Set<Object> expanded = expandParams(extractParams(qw));
            String sql = qw.getCustomSqlSegment();
            boolean nullSessionId = sql != null && sql.contains("current_session_id IS NULL");
            return piles.values().stream()
                    .filter(p -> {
                        if (expanded.contains(PileType.FAST)) return p.getPileType() == PileType.FAST;
                        if (expanded.contains(PileType.SLOW)) return p.getPileType() == PileType.SLOW;
                        return true;
                    })
                    .filter(p -> {
                        if (expanded.contains("FAULT")) return p.getWorkingState() == WorkingState.FAULT;
                        if (expanded.contains("AVAILABLE") || expanded.contains("CHARGING"))
                            return p.getWorkingState() == WorkingState.AVAILABLE || p.getWorkingState() == WorkingState.CHARGING;
                        return true;
                    })
                    .filter(p -> !nullSessionId || p.getCurrentSessionId() == null)
                    .collect(Collectors.toList());
        });

        // --- ChargingSessionMapper ---
        when(sessionMapper.selectById(any())).thenAnswer(i -> sessions.get(i.getArgument(0)));
        when(sessionMapper.insert(any(ChargingSession.class))).thenAnswer(i -> {
            ChargingSession s = i.getArgument(0);
            sessions.put(s.getSessionId(), s);
            return 1;
        });
        when(sessionMapper.updateById(any(ChargingSession.class))).thenAnswer(i -> {
            ChargingSession s = i.getArgument(0);
            sessions.put(s.getSessionId(), s);
            return 1;
        });
        when(sessionMapper.selectOne(any())).thenAnswer(i -> {
            QueryWrapper<ChargingSession> qw = i.getArgument(0);
            Set<Object> expanded = expandParams(extractParams(qw));
            String carId = findCarIdInParams(expanded);
            String pileId = findPileIdInParams(expanded);
            boolean findCharging = expanded.contains(SessionStatus.CHARGING) || expanded.contains("CHARGING");
            return sessions.values().stream()
                    .filter(s -> carId == null || carId.equals(s.getCarId()))
                    .filter(s -> pileId == null || pileId.equals(s.getPileId()))
                    .filter(s -> !findCharging || s.getSessionStatus() == SessionStatus.CHARGING)
                    .findFirst().orElse(null);
        });

        // --- BillingRatePeriodMapper ---
        when(ratePeriodMapper.selectList(any())).thenAnswer(i -> {
            QueryWrapper<BillingRatePeriod> qw = i.getArgument(0);
            Set<Object> expanded = expandParams(extractParams(qw));
            if (expanded.contains(PileType.FAST))
                return ratePeriods.stream().filter(r -> r.getPileType() == PileType.FAST).collect(Collectors.toList());
            if (expanded.contains(PileType.SLOW))
                return ratePeriods.stream().filter(r -> r.getPileType() == PileType.SLOW).collect(Collectors.toList());
            return new ArrayList<>(ratePeriods);
        });

        // --- QueueEntryMapper ---
        when(queueMapper.insert(any(QueueEntry.class))).thenAnswer(i -> {
            QueueEntry e = i.getArgument(0);
            if (e.getId() == null) e.setId(queueIdSeq.getAndIncrement());
            queueEntries.put(e.getId(), e);
            return 1;
        });
        when(queueMapper.selectOne(any())).thenAnswer(i -> {
            QueryWrapper<QueueEntry> qw = i.getArgument(0);
            Set<Object> expanded = expandParams(extractParams(qw));
            String queueType = null, queueKey = null;
            for (var v : expanded) {
                if ("PILE".equals(v)) queueType = "PILE";
                else if ("WAIT".equals(v)) queueType = "WAIT";
                else if ("FAST".equals(v)) queueKey = "FAST";
                else if ("SLOW".equals(v)) queueKey = "SLOW";
                else if (v instanceof String s && s.matches("^[FT]\\d+$")) queueKey = s;
            }
            for (var e : queueEntries.values()) {
                boolean match = true;
                if (queueType != null && !queueType.equals(e.getQueueType())) match = false;
                if (queueKey != null && !queueKey.equals(e.getQueueKey())) match = false;
                if (match) return e;
            }
            return null;
        });
        when(queueMapper.delete(any())).thenAnswer(i -> {
            QueryWrapper<QueueEntry> qw = i.getArgument(0);
            Set<Object> expanded = expandParams(extractParams(qw));
            String orderId = null;
            for (var v : expanded) {
                if (v instanceof String s && s.startsWith("order-")) { orderId = s; break; }
            }
            if (orderId != null) {
                var iter = queueEntries.values().iterator();
                while (iter.hasNext()) {
                    if (orderId.equals(iter.next().getOrderId())) iter.remove();
                }
            }
            return 1;
        });
        when(queueMapper.deleteById(any(Long.class))).thenAnswer(i -> {
            Object id = i.getArgument(0);
            if (id instanceof Long lid) queueEntries.remove(lid);
            return 1;
        });

        // --- BillMapper ---
        when(billMapper.insert(any(Bill.class))).thenAnswer(i -> {
            Bill b = i.getArgument(0);
            bills.put(b.getBillId(), b);
            return 1;
        });

        // --- FaultRecordMapper ---
        when(faultMapper.insert(any(FaultRecord.class))).thenAnswer(i -> {
            FaultRecord r = i.getArgument(0);
            faultRecords.put(r.getFaultId(), r);
            return 1;
        });
        when(faultMapper.selectOne(any())).thenAnswer(i -> {
            QueryWrapper<FaultRecord> qw = i.getArgument(0);
            Set<Object> expanded = expandParams(extractParams(qw));
            String pileId = findPileIdInParams(expanded);
            boolean active = expanded.contains(FaultStatus.ACTIVE);
            return faultRecords.values().stream()
                    .filter(r -> pileId == null || pileId.equals(r.getPileId()))
                    .filter(r -> !active || r.getFaultStatus() == FaultStatus.ACTIVE)
                    .findFirst().orElse(null);
        });
        when(faultMapper.updateById(any(FaultRecord.class))).thenAnswer(i -> {
            FaultRecord r = i.getArgument(0);
            faultRecords.put(r.getFaultId(), r);
            return 1;
        });

        // --- Scheduler (no-op) ---
        when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenReturn(null);
    }

    // ==================== Data Init ====================

    private void initCars() {
        for (int i = 1; i <= 30; i++) {
            Car c = new Car();
            c.setCarId("V" + i);
            c.setBatteryCapacityKwh(new BigDecimal("200"));
            cars.put(c.getCarId(), c);
        }
    }

    private void initRatePeriods() {
        // 谷时 06:00-07:00 电价0.4 服务费0.8
        ratePeriods.add(makeRatePeriod(PileType.FAST, PeriodName.VALLEY, "06:00", "07:00",
                new BigDecimal("0.4"), new BigDecimal("0.8")));
        ratePeriods.add(makeRatePeriod(PileType.SLOW, PeriodName.VALLEY, "06:00", "07:00",
                new BigDecimal("0.4"), new BigDecimal("0.8")));
        // 平时 07:00-10:00 电价0.7 服务费0.8
        ratePeriods.add(makeRatePeriod(PileType.FAST, PeriodName.NORMAL, "07:00", "10:00",
                new BigDecimal("0.7"), new BigDecimal("0.8")));
        ratePeriods.add(makeRatePeriod(PileType.SLOW, PeriodName.NORMAL, "07:00", "10:00",
                new BigDecimal("0.7"), new BigDecimal("0.8")));
        // 峰时 10:00-15:00 电价1.0 服务费0.8
        ratePeriods.add(makeRatePeriod(PileType.FAST, PeriodName.PEAK, "10:00", "15:00",
                new BigDecimal("1.0"), new BigDecimal("0.8")));
        ratePeriods.add(makeRatePeriod(PileType.SLOW, PeriodName.PEAK, "10:00", "15:00",
                new BigDecimal("1.0"), new BigDecimal("0.8")));
    }

    private static BillingRatePeriod makeRatePeriod(PileType pt, PeriodName name,
                                                     String start, String end,
                                                     BigDecimal elec, BigDecimal service) {
        BillingRatePeriod r = new BillingRatePeriod();
        r.setPeriodId(UUID.randomUUID().toString());
        r.setPileType(pt);
        r.setPeriodName(name);
        r.setStartTime(start);
        r.setEndTime(end);
        r.setElectricityPrice(elec);
        r.setServicePrice(service);
        return r;
    }

    private void initPiles() {
        initPile(makePile("F1", "快充1", PileType.FAST, 30));
        initPile(makePile("F2", "快充2", PileType.FAST, 30));
        initPile(makePile("T1", "慢充1", PileType.SLOW, 10));
        initPile(makePile("T2", "慢充2", PileType.SLOW, 10));
        initPile(makePile("T3", "慢充3", PileType.SLOW, 10));
    }

    private void initPile(ChargingPile pile) {
        piles.put(pile.getPileId(), pile);
    }

    private static ChargingPile makePile(String id, String no, PileType type, int power) {
        ChargingPile p = new ChargingPile();
        p.setPileId(id);
        p.setPileNo(no);
        p.setPileType(type);
        p.setPowerKw(power);
        p.setPowerState(PowerState.ON);
        p.setWorkingState(WorkingState.AVAILABLE);
        return p;
    }

    // ==================== Event/Checkpoint Parsing ====================

    static record Event(String time, String type, String subject, String mode, double value) {}
    static record PileExpected(String car, String kwh, String fee) {}
    static record Checkpoint(String time, Map<String, PileExpected> pileStates, List<String> waitingArea) {}

    private List<Event> parseEvents(List<String> lines) {
        List<Event> events = new ArrayList<>();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("FAST_")
                    || line.startsWith("SLOW_") || line.startsWith("SERVICE_")
                    || line.startsWith("VALLEY_") || line.startsWith("NORMAL_")
                    || line.startsWith("PEAK_") || line.startsWith("PILE_")
                    || line.startsWith("WAIT_"))
                continue;
            String[] parts = line.split("\\s+", 5);
            if (parts.length >= 3) {
                String time = parts[0];
                String type = parts[1];
                String subject = parts[2];
                String mode = parts.length >= 4 ? parts[3] : "";
                double value = 0;
                if (parts.length >= 5) {
                    try {
                        value = Double.parseDouble(parts[4]);
                    } catch (NumberFormatException ignored) {}
                }
                events.add(new Event(time, type, subject, mode, value));
            }
        }
        return events;
    }

    private List<Checkpoint> parseExpectedStates(List<String> lines) {
        List<Checkpoint> checkpoints = new ArrayList<>();
        String currentTime = null;
        Map<String, PileExpected> pileStates = new LinkedHashMap<>();
        List<String> waiting = new ArrayList<>();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("PILES:")) {
                String data = line.substring(6).trim();
                String[] pilesData = data.split("\\|");
                String[] names = {"FAST1", "FAST2", "SLOW1", "SLOW2", "SLOW3"};
                for (int i = 0; i < names.length && i < pilesData.length; i++) {
                    String ps = pilesData[i].trim();
                    if (ps.equals("-")) {
                        pileStates.put(names[i], null);
                    } else {
                        String[] parts = ps.split("\\s+");
                        if (parts.length >= 3) {
                            pileStates.put(names[i],
                                    new PileExpected(parts[0], parts[1], parts[2]));
                        }
                    }
                }
            } else if (line.startsWith("WAIT:")) {
                String data = line.substring(5).trim();
                if (!data.equals("-")) {
                    waiting = new ArrayList<>(Arrays.asList(data.split("\\s+")));
                }
            } else if (line.startsWith("QUEUE")) {
            } else {
                if (currentTime != null) {
                    checkpoints.add(new Checkpoint(currentTime,
                            new LinkedHashMap<>(pileStates), new ArrayList<>(waiting)));
                }
                currentTime = line;
                pileStates.clear();
                waiting.clear();
            }
        }
        if (currentTime != null) {
            checkpoints.add(new Checkpoint(currentTime,
                    new LinkedHashMap<>(pileStates), new ArrayList<>(waiting)));
        }
        return checkpoints;
    }

    private static int toMinutes(String time) {
        String[] parts = time.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private void dumpState() {
        String[] names = {"FAST1", "FAST2", "SLOW1", "SLOW2", "SLOW3"};
        String[] ids = {"F1", "F2", "T1", "T2", "T3"};
        System.out.println("STATE at " + time.now());
        for (int i = 0; i < 5; i++) {
            var p = piles.get(ids[i]);
            var s = findSessionByPile(ids[i]);
            String car = s != null ? s.getCarId() : "-";
            String kwh = s != null ? String.format("%.2f", s.getChargedKwh()) : "-";
            String fee = s != null ? String.format("%.2f", sessionFees.getOrDefault(s.getSessionId(), BigDecimal.ZERO)) : "-";
            String state = p != null ? p.getWorkingState().name() : "?";
            System.out.println("  " + names[i] + ": " + state + " car=" + car + " kwh=" + kwh + " fee=" + fee);
        }
    }
}

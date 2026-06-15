package bupt.evchargebackend.schedule.driver;

import bupt.evchargebackend.entity.charging.ChargingSession;
import bupt.evchargebackend.entity.charging.enums.RequestMode;
import bupt.evchargebackend.entity.pile.ChargingPile;
import bupt.evchargebackend.entity.pile.enums.PileType;
import bupt.evchargebackend.entity.pile.enums.PowerState;
import bupt.evchargebackend.entity.pile.enums.WorkingState;
import bupt.evchargebackend.schedule.stub.BillingHelper;
import bupt.evchargebackend.schedule.stub.ChargingService;
import bupt.evchargebackend.schedule.stub.SimulatedTimeProvider;
import bupt.evchargebackend.schedule.stub.Stub;
import bupt.evchargebackend.service.schedule.SchedulingEngine;
import bupt.evchargebackend.service.schedule.impl.FifoStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 调度引擎验收测试驱动：读取测试用例和预期结果，按 5 分钟步进驱动仿真。
 *
 * <p>仿真流程：
 * <ul>
 *   <li>checkpoint → 边界故障检测 → 步进(事件处理 + 充电) → 剩余事件 → verify</li>
 *   <li>边界步进前若有 FAULT 事件，设 deferWaitDispatch 抑制 fillSlot 误从等候区分发</li>
 * </ul>
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
class AcceptanceTestDriver {

    private static final Path CASE_FILE = Path.of("src/test/resources/schedule/test-case.txt");
    private static final Path EXPECTED_FILE = Path.of("src/test/resources/schedule/test-expected.txt");
    private static final int TIME_STEP = 5;
    private static final int SIMULATION_START_HOUR = 6;

    private Stub stub;
    private SchedulingEngine engine;
    private ChargingService service;
    private SimulatedTimeProvider time;

    @BeforeEach
    void setUp() {
        time = new SimulatedTimeProvider(LocalDateTime.of(2026, 6, 14, SIMULATION_START_HOUR, 0));
        stub = new Stub();
        engine = new SchedulingEngine(new FifoStrategy(), 3);
        service = new ChargingService(stub, engine, time);
        initPiles();
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
                // 边界检查：checkpoint时刻是否有FAULT事件（影响fillSlot）
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
        for (var session : stub.sessions.values()) {
            if (session.getSessionStatus() != bupt.evchargebackend.entity.charging.enums.SessionStatus.CHARGING)
                continue;
            var pile = stub.getPile(session.getPileId());
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
            stub.addSessionFee(session.getSessionId(), fee);
        }
        for (var session : finished) {
            service.finish(session.getCarId());
        }
    }

    private void processEvent(Event event) {
        switch (event.type) {
            case "ARRIVE" -> {
                RequestMode mode = event.mode.equals("FAST") ? RequestMode.FAST : RequestMode.SLOW;
                service.submit(event.subject, mode, event.value);
            }
            case "FINISH" -> service.finish(event.subject);
            case "FAULT" -> service.fault(event.subject);
            case "RECOVER" -> service.recover(event.subject);
            case "CHANGE" -> service.changeAmount(event.subject, event.value);
        }
    }

    private void verify(Checkpoint cp) {
        String[] pileNames = {"FAST1", "FAST2", "SLOW1", "SLOW2", "SLOW3"};
        String[] pileIds = {"F1", "F2", "T1", "T2", "T3"};
        for (int i = 0; i < 5; i++) {
            var expected = cp.pileStates.get(pileNames[i]);
            var pile = stub.getPile(pileIds[i]);
            boolean isFault = pile != null && pile.getWorkingState() == WorkingState.FAULT;
            if (expected == null) {
                var active = stub.findSessionByPile(pileIds[i]);
                if (active != null) {
                    fail(pileNames[i] + " expected empty at " + cp.time + " but has " + active.getCarId());
                }
            } else {
                var session = isFault ? stub.findLastSessionByPile(pileIds[i])
                        : stub.findSessionByPile(pileIds[i]);
                String actualCar = session != null ? session.getCarId() : "-";
                assertEquals(expected.car, actualCar, pileNames[i] + " car mismatch at " + cp.time);

                String actualKwh = session != null
                        ? String.format("%.2f", session.getChargedKwh()) : "-";
                assertEquals(expected.kwh, actualKwh,
                        pileNames[i] + " kwh mismatch at " + cp.time);

                String actualFee = session != null
                        ? String.format("%.2f", stub.getSessionFee(session.getSessionId())) : "-";
                assertEquals(expected.fee, actualFee,
                        pileNames[i] + " fee mismatch at " + cp.time);
            }
        }
    }

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
                String[] piles = data.split("\\|");
                String[] names = {"FAST1", "FAST2", "SLOW1", "SLOW2", "SLOW3"};
                for (int i = 0; i < names.length && i < piles.length; i++) {
                    String ps = piles[i].trim();
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
            var p = stub.getPile(ids[i]);
            var s = stub.findSessionByPile(ids[i]);
            String car = s != null ? s.getCarId() : "-";
            String kwh = s != null ? String.format("%.2f", s.getChargedKwh()) : "-";
            String fee = s != null ? String.format("%.2f", stub.getSessionFee(s.getSessionId())) : "-";
            String state = p != null ? p.getWorkingState().name() : "?";
            System.out.println("  " + names[i] + ": " + state + " car=" + car + " kwh=" + kwh + " fee=" + fee);
        }
    }

    private void initPiles() {
        stub.initPile(makePile("F1", "快充1", PileType.FAST, 30));
        stub.initPile(makePile("F2", "快充2", PileType.FAST, 30));
        stub.initPile(makePile("T1", "慢充1", PileType.SLOW, 10));
        stub.initPile(makePile("T2", "慢充2", PileType.SLOW, 10));
        stub.initPile(makePile("T3", "慢充3", PileType.SLOW, 10));
    }

    private ChargingPile makePile(String id, String no, PileType type, int power) {
        ChargingPile p = new ChargingPile();
        p.setPileId(id);
        p.setPileNo(no);
        p.setPileType(type);
        p.setPowerKw(power);
        p.setPowerState(PowerState.ON);
        p.setWorkingState(WorkingState.AVAILABLE);
        return p;
    }
}

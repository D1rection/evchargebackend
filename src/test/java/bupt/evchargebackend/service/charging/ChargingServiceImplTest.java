package bupt.evchargebackend.service.charging;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.common.time.TimeProvider;
import bupt.evchargebackend.dto.charging.ChargingRequest;
import bupt.evchargebackend.dto.charging.ChargingResponse;
import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.charging.ChargingSession;
import bupt.evchargebackend.entity.charging.enums.OrderStatus;
import bupt.evchargebackend.entity.charging.enums.SessionStatus;
import bupt.evchargebackend.dto.charging.ChargingStartRequest;
import bupt.evchargebackend.entity.pile.ChargingPile;
import bupt.evchargebackend.entity.pile.enums.PileType;
import bupt.evchargebackend.entity.pile.enums.PowerState;
import bupt.evchargebackend.entity.pile.enums.WorkingState;
import bupt.evchargebackend.entity.pricing.BillingRatePeriod;
import bupt.evchargebackend.entity.user.Car;
import bupt.evchargebackend.mapper.charging.ChargingOrderMapper;
import bupt.evchargebackend.mapper.charging.ChargingSessionMapper;
import bupt.evchargebackend.mapper.pile.ChargingPileMapper;
import bupt.evchargebackend.mapper.pricing.BillingRatePeriodMapper;
import bupt.evchargebackend.mapper.queue.QueueEntryMapper;
import bupt.evchargebackend.mapper.user.CarMapper;
import bupt.evchargebackend.service.charging.impl.ChargingServiceImpl;
import bupt.evchargebackend.service.schedule.SchedulingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ChargingServiceImplTest {

    private CarMapper carMapper;
    private ChargingOrderMapper chargingOrderMapper;
    private ChargingPileMapper chargingPileMapper;
    private ChargingSessionMapper chargingSessionMapper;
    private BillingRatePeriodMapper billingRatePeriodMapper;
    private QueueEntryMapper queueEntryMapper;
    private SchedulingEngine engine;
    private TimeProvider timeProvider;
    private ChargingServiceImpl service;

    private static final String CAR_ID = "C001";
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(30);
    private static final BigDecimal BATTERY_CAPACITY = BigDecimal.valueOf(60);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 15, 14, 0);

    private ChargingRequest request;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        carMapper = mock(CarMapper.class);
        chargingOrderMapper = mock(ChargingOrderMapper.class);
        chargingPileMapper = mock(ChargingPileMapper.class);
        chargingSessionMapper = mock(ChargingSessionMapper.class);
        billingRatePeriodMapper = mock(BillingRatePeriodMapper.class);
        queueEntryMapper = mock(QueueEntryMapper.class);
        engine = mock(SchedulingEngine.class);
        timeProvider = mock(TimeProvider.class);

        service = new ChargingServiceImpl(chargingOrderMapper, carMapper,
                engine, chargingPileMapper, chargingSessionMapper,
                billingRatePeriodMapper, queueEntryMapper, timeProvider);

        Car car = new Car();
        car.setCarId(CAR_ID);
        car.setBatteryCapacityKwh(BATTERY_CAPACITY);

        request = new ChargingRequest();
        request.setCarId(CAR_ID);
        request.setRequestAmount(AMOUNT);
        request.setRequestMode("FAST");

        doReturn(car).when(carMapper).selectById(CAR_ID);
        doReturn(0L).when(chargingOrderMapper).selectCount(any());
        doReturn(NOW).when(timeProvider).now();
        doReturn(List.of()).when(billingRatePeriodMapper).selectList(any());
        doReturn(List.of()).when(engine).getPileQueue(anyString());
    }

    // ========== 参数校验 ==========

    @Test
    void shouldReturn400_whenCarIdIsEmpty() {
        request.setCarId(null);
        assertEquals(400, service.submit(request).getCode());
    }

    @Test
    void shouldReturn404_whenCarNotFound() {
        doReturn(null).when(carMapper).selectById(CAR_ID);
        assertEquals(404, service.submit(request).getCode());
    }

    @Test
    void shouldReturn400_whenAmountIsNull() {
        request.setRequestAmount(null);
        assertEquals(400, service.submit(request).getCode());
    }

    @Test
    void shouldReturn400_whenAmountIsZero() {
        request.setRequestAmount(BigDecimal.ZERO);
        assertEquals(400, service.submit(request).getCode());
    }

    @Test
    void shouldReturn400_whenAmountIsNegative() {
        request.setRequestAmount(BigDecimal.valueOf(-10));
        assertEquals(400, service.submit(request).getCode());
    }

    @Test
    void shouldReturn400_whenAmountExceedsBatteryCapacity() {
        request.setRequestAmount(BigDecimal.valueOf(100));
        assertEquals(400, service.submit(request).getCode());
    }

    @Test
    void shouldReturn400_whenModeIsEmpty() {
        request.setRequestMode(null);
        assertEquals(400, service.submit(request).getCode());
    }

    @Test
    void shouldReturn400_whenModeIsInvalid() {
        request.setRequestMode("INVALID");
        assertEquals(400, service.submit(request).getCode());
    }

    @Test
    void shouldReturn400_whenCarHasActiveOrder() {
        doReturn(1L).when(chargingOrderMapper).selectCount(any());
        assertEquals(400, service.submit(request).getCode());
    }

    // ========== 调度逻辑 ==========

    @Test
    void shouldDispatchToPileQueue_whenNoFaultAndQueueHasRoom() {
        doReturn(false).when(engine).hasAnyFault();
        doReturn(mutableList(createFastPile("F1"))).when(chargingPileMapper).selectList(any());
        doReturn(true).when(engine).addToPileQueue(anyString(), any());
        doReturn(2).when(engine).pileQueueSize(anyString());

        Result<ChargingResponse> result = service.submit(request);
        assertEquals(200, result.getCode());
        assertEquals("called", result.getData().getCarState());
        assertEquals(Integer.valueOf(2), result.getData().getQueueNum());
    }

    @Test
    void shouldEnqueueWait_whenNoFaultAndQueueFull() {
        doReturn(false).when(engine).hasAnyFault();
        doReturn(mutableList(createFastPile("F1"))).when(chargingPileMapper).selectList(any());
        doReturn(false).when(engine).addToPileQueue(anyString(), any());
        doReturn(3).when(engine).waitQueueSize(any());

        Result<ChargingResponse> result = service.submit(request);
        assertEquals(200, result.getCode());
        assertEquals("waiting", result.getData().getCarState());
        assertEquals(Integer.valueOf(3), result.getData().getQueueNum());
    }

    @Test
    void shouldEnqueueWait_whenFaultExists() {
        doReturn(true).when(engine).hasAnyFault();
        doReturn(1).when(engine).waitQueueSize(any());

        Result<ChargingResponse> result = service.submit(request);
        assertEquals(200, result.getCode());
        assertEquals("waiting", result.getData().getCarState());
    }

    @Test
    void shouldEnqueueWait_whenNoNonFaultPiles() {
        doReturn(false).when(engine).hasAnyFault();
        doReturn(List.of()).when(chargingPileMapper).selectList(any());
        doReturn(2).when(engine).waitQueueSize(any());

        Result<ChargingResponse> result = service.submit(request);
        assertEquals(200, result.getCode());
        assertEquals("waiting", result.getData().getCarState());
        assertEquals(Integer.valueOf(2), result.getData().getQueueNum());
    }

    @Test
    void shouldSortPilesByTotalActiveMinutes() {
        ChargingPile fast1 = createFastPile("F1");
        ChargingPile fast2 = createFastPile("F2");
        fast1.setCurrentSessionId("session-1");
        doReturn(createSession(20)).when(chargingSessionMapper).selectById("session-1");

        doReturn(false).when(engine).hasAnyFault();
        doReturn(mutableList(fast1, fast2)).when(chargingPileMapper).selectList(any());
        doReturn(true).when(engine).addToPileQueue(anyString(), any());

        service.submit(request);
        // totalActiveMinutes: F1=40min, F2=0min → 选 F2
        verify(engine).addToPileQueue(eq("F2"), any());
    }

    // ========== 业务计算 ==========

    @Test
    void shouldCalculateFastModeMinutes() {
        doReturn(false).when(engine).hasAnyFault();
        doReturn(mutableList(createFastPile("F1"))).when(chargingPileMapper).selectList(any());
        doReturn(true).when(engine).addToPileQueue(anyString(), any());

        Result<ChargingResponse> result = service.submit(request);
        assertEquals(Integer.valueOf(60), result.getData().getEstimatedMinutes());
    }

    @Test
    void shouldCalculateSlowModeMinutes() {
        request.setRequestMode("SLOW");
        doReturn(false).when(engine).hasAnyFault();
        doReturn(mutableList(createSlowPile("T1"))).when(chargingPileMapper).selectList(any());
        doReturn(true).when(engine).addToPileQueue(anyString(), any());

        Result<ChargingResponse> result = service.submit(request);
        assertEquals(Integer.valueOf(180), result.getData().getEstimatedMinutes());
    }

    @Test
    void shouldCalculateEstimatedFeeWithFallbackRate() {
        doReturn(false).when(engine).hasAnyFault();
        doReturn(mutableList(createFastPile("F1"))).when(chargingPileMapper).selectList(any());
        doReturn(true).when(engine).addToPileQueue(anyString(), any());

        Result<ChargingResponse> result = service.submit(request);
        assertEquals(new BigDecimal("30.00"), result.getData().getEstimatedFee());
    }

    @Test
    void shouldCalculateEstimatedFeeWithMatchedRate() {
        BillingRatePeriod period = new BillingRatePeriod();
        period.setStartTime("08:00");
        period.setEndTime("18:00");
        period.setElectricityPrice(new BigDecimal("1.2"));
        period.setServicePrice(new BigDecimal("0.3"));

        doReturn(mutableList(period)).when(billingRatePeriodMapper).selectList(any());
        doReturn(false).when(engine).hasAnyFault();
        doReturn(mutableList(createFastPile("F1"))).when(chargingPileMapper).selectList(any());
        doReturn(true).when(engine).addToPileQueue(anyString(), any());

        Result<ChargingResponse> result = service.submit(request);
        assertEquals(new BigDecimal("45.00"), result.getData().getEstimatedFee());
    }

    // ========== 持久化验证 ==========

    @Test
    void shouldPersistOrderWithEstimatedValues() {
        doReturn(false).when(engine).hasAnyFault();
        doReturn(mutableList(createFastPile("F1"))).when(chargingPileMapper).selectList(any());
        doReturn(true).when(engine).addToPileQueue(anyString(), any());
        doReturn(1).when(engine).pileQueueSize(anyString());

        service.submit(request);

        var captor = org.mockito.ArgumentCaptor.forClass(ChargingOrder.class);
        verify(chargingOrderMapper).insert(captor.capture());
        ChargingOrder inserted = captor.getValue();
        assertEquals(CAR_ID, inserted.getCarId());
        assertEquals(AMOUNT, inserted.getTargetKwh());
        assertEquals(new BigDecimal("30.00"), inserted.getEstimatedFee());
        assertEquals(Integer.valueOf(60), inserted.getEstimatedMinutes());
    }

    // ========== 开始充电 ==========

    @Test
    void startShouldReturn400_whenCarIdIsEmpty() {
        ChargingStartRequest req = new ChargingStartRequest();
        req.setChargePileNum("F1");
        assertEquals(400, service.start(req).getCode());
    }

    @Test
    void startShouldReturn404_whenCarNotFound() {
        ChargingStartRequest req = new ChargingStartRequest();
        req.setCarId(CAR_ID);
        req.setChargePileNum("F1");
        doReturn(null).when(carMapper).selectById(CAR_ID);
        assertEquals(404, service.start(req).getCode());
    }

    @Test
    void startShouldReturn400_whenNoCalledOrder() {
        ChargingStartRequest req = new ChargingStartRequest();
        req.setCarId(CAR_ID);
        req.setChargePileNum("F1");
        doReturn(null).when(chargingOrderMapper).selectOne(any());
        assertEquals(400, service.start(req).getCode());
    }

    @Test
    void startShouldReturn404_whenPileNotFound() {
        ChargingStartRequest req = new ChargingStartRequest();
        req.setCarId(CAR_ID);
        req.setChargePileNum("F1");
        doReturn(createCalledOrder()).when(chargingOrderMapper).selectOne(any());
        doReturn(null).when(chargingPileMapper).selectById("F1");
        assertEquals(404, service.start(req).getCode());
    }

    @Test
    void startShouldReturn400_whenPowerOff() {
        ChargingPile pile = createFastPile("F1");
        pile.setPowerState(PowerState.OFF);

        ChargingStartRequest req = new ChargingStartRequest();
        req.setCarId(CAR_ID);
        req.setChargePileNum("F1");
        doReturn(createCalledOrder()).when(chargingOrderMapper).selectOne(any());
        doReturn(pile).when(chargingPileMapper).selectById("F1");
        assertEquals(400, service.start(req).getCode());
    }

    @Test
    void startShouldReturn400_whenPileNotAvailable() {
        ChargingPile pile = createFastPile("F1");
        pile.setWorkingState(WorkingState.FAULT);

        ChargingStartRequest req = new ChargingStartRequest();
        req.setCarId(CAR_ID);
        req.setChargePileNum("F1");
        doReturn(createCalledOrder()).when(chargingOrderMapper).selectOne(any());
        doReturn(pile).when(chargingPileMapper).selectById("F1");
        assertEquals(400, service.start(req).getCode());
    }

    @Test
    void startShouldReturn400_whenNotQueueHead() {
        ChargingOrder order = createCalledOrder();
        ChargingPile pile = createFastPile("F1");
        pile.setPowerState(PowerState.ON);

        ChargingStartRequest req = new ChargingStartRequest();
        req.setCarId(CAR_ID);
        req.setChargePileNum("F1");
        doReturn(order).when(chargingOrderMapper).selectOne(any());
        doReturn(pile).when(chargingPileMapper).selectById("F1");
        doReturn(null).when(engine).peekPileQueue("F1");
        assertEquals(400, service.start(req).getCode());
    }

    @Test
    void startShouldSucceed() {
        ChargingOrder order = createCalledOrder();
        ChargingPile pile = createFastPile("F1");
        pile.setPowerState(PowerState.ON);

        ChargingStartRequest req = new ChargingStartRequest();
        req.setCarId(CAR_ID);
        req.setChargePileNum("F1");
        doReturn(order).when(chargingOrderMapper).selectOne(any());
        doReturn(pile).when(chargingPileMapper).selectById("F1");
        doReturn(order).when(engine).peekPileQueue("F1");

        var result = service.start(req);
        assertEquals(200, result.getCode());
        assertEquals(Integer.valueOf(1), result.getData().getResult());
    }

    // ========== Helper ==========

    @SafeVarargs
    private static <T> List<T> mutableList(T... items) {
        return new ArrayList<>(List.of(items));
    }

    private static ChargingOrder createCalledOrder() {
        ChargingOrder o = new ChargingOrder();
        o.setOrderId("order-1");
        o.setCarId(CAR_ID);
        o.setOrderStatus(OrderStatus.CALLED);
        o.setTargetKwh(AMOUNT);
        return o;
    }

    private static ChargingPile createFastPile(String id) {
        ChargingPile p = new ChargingPile();
        p.setPileId(id);
        p.setPileType(PileType.FAST);
        p.setPowerKw(30);
        p.setWorkingState(WorkingState.AVAILABLE);
        return p;
    }

    private static ChargingPile createSlowPile(String id) {
        ChargingPile p = new ChargingPile();
        p.setPileId(id);
        p.setPileType(PileType.SLOW);
        p.setPowerKw(10);
        p.setWorkingState(WorkingState.AVAILABLE);
        return p;
    }

    private static ChargingSession createSession(int remainingKwh) {
        ChargingSession s = new ChargingSession();
        s.setTargetKwh(BigDecimal.valueOf(remainingKwh));
        s.setChargedKwh(BigDecimal.ZERO);
        s.setSessionStatus(SessionStatus.CHARGING);
        return s;
    }
}

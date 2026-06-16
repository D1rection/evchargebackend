package bupt.evchargebackend.service.charging;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.common.time.TimeProvider;
import bupt.evchargebackend.dto.charging.ChargingRequest;
import bupt.evchargebackend.dto.charging.ChargingResponse;
import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.charging.ChargingSession;
import bupt.evchargebackend.entity.charging.enums.OrderStatus;
import bupt.evchargebackend.entity.charging.enums.RequestMode;
import bupt.evchargebackend.entity.charging.enums.SessionStatus;
import bupt.evchargebackend.dto.charging.ChargingCancelRequest;
import bupt.evchargebackend.dto.charging.ChargingEndRequest;
import bupt.evchargebackend.entity.pile.ChargingPile;
import bupt.evchargebackend.entity.pile.enums.PileType;
import bupt.evchargebackend.entity.pile.enums.PowerState;
import bupt.evchargebackend.entity.pile.enums.WorkingState;
import bupt.evchargebackend.entity.bill.Bill;
import bupt.evchargebackend.entity.queue.QueueEntry;
import bupt.evchargebackend.entity.pricing.BillingRatePeriod;
import bupt.evchargebackend.entity.user.Car;
import bupt.evchargebackend.mapper.charging.ChargingOrderMapper;
import bupt.evchargebackend.mapper.charging.ChargingSessionMapper;
import bupt.evchargebackend.mapper.pile.ChargingPileMapper;
import bupt.evchargebackend.mapper.pricing.BillingRatePeriodMapper;
import bupt.evchargebackend.mapper.bill.BillMapper;
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
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChargingServiceImplTest {

    private CarMapper carMapper;
    private ChargingOrderMapper chargingOrderMapper;
    private ChargingPileMapper chargingPileMapper;
    private ChargingSessionMapper chargingSessionMapper;
    private BillingRatePeriodMapper billingRatePeriodMapper;
    private QueueEntryMapper queueEntryMapper;
    private BillMapper billMapper;
    private ScheduledExecutorService scheduler;
    private SchedulingEngine engine;
    private TimeProvider timeProvider;
    private ChargingServiceImpl service;

    private static final String CAR_ID = "C001";
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(30);
    private static final BigDecimal BATTERY_CAPACITY = BigDecimal.valueOf(60);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 15, 14, 0);

    private ChargingRequest request;

    @BeforeEach
    void setUp() {
        carMapper = mock(CarMapper.class);
        chargingOrderMapper = mock(ChargingOrderMapper.class);
        chargingPileMapper = mock(ChargingPileMapper.class);
        chargingSessionMapper = mock(ChargingSessionMapper.class);
        billingRatePeriodMapper = mock(BillingRatePeriodMapper.class);
        queueEntryMapper = mock(QueueEntryMapper.class);
        billMapper = mock(BillMapper.class);
        scheduler = mock(ScheduledExecutorService.class);
        engine = mock(SchedulingEngine.class);
        timeProvider = mock(TimeProvider.class);

        service = new ChargingServiceImpl(chargingOrderMapper, carMapper,
                engine, chargingPileMapper, chargingSessionMapper,
                billingRatePeriodMapper, queueEntryMapper, billMapper, scheduler, timeProvider);

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
        doReturn(mutableList(period("00:00", "24:00", "1.0", "0"))).when(billingRatePeriodMapper).selectList(any());
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
        doReturn(0).when(engine).pileQueueSize(anyString());

        Result<ChargingResponse> result = service.submit(request);
        assertEquals(200, result.getCode());
        assertEquals("charging", result.getData().getCarState());
        assertEquals(Integer.valueOf(0), result.getData().getQueueNum());
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
    void shouldPersistOrder() {
        doReturn(false).when(engine).hasAnyFault();
        doReturn(mutableList(createFastPile("F1"))).when(chargingPileMapper).selectList(any());
        doReturn(true).when(engine).addToPileQueue(anyString(), any());

        service.submit(request);

        var captor = org.mockito.ArgumentCaptor.forClass(ChargingOrder.class);
        verify(chargingOrderMapper).insert(captor.capture());
        ChargingOrder inserted = captor.getValue();
        assertEquals(CAR_ID, inserted.getCarId());
        assertEquals(AMOUNT, inserted.getTargetKwh());
    }

    // ========== 查看队列状态 ==========

    @Test
    void queueStatusShouldReturn400_whenCarIdIsEmpty() {
        assertEquals(400, service.queueStatus("").getCode());
    }

    @Test
    void queueStatusShouldReturn404_whenNoOrder() {
        doReturn(null).when(chargingOrderMapper).selectOne(any());
        assertEquals(404, service.queueStatus(CAR_ID).getCode());
    }

    @Test
    void queueStatusShouldReturnWaiting_whenPosition0() {
        ChargingOrder order = createOrder(OrderStatus.WAITING);
        order.setRequestMode(RequestMode.FAST);
        doReturn(order).when(chargingOrderMapper).selectOne(any());
        doReturn(0).when(engine).waitPosition(PileType.FAST, order.getOrderId());

        var result = service.queueStatus(CAR_ID);
        assertEquals(200, result.getCode());
        assertEquals("waiting", result.getData().getCarState());
        assertEquals(Integer.valueOf(1), result.getData().getQueueNum());
        assertEquals(Integer.valueOf(0), result.getData().getCarNumberBeforePosition());
    }

    @Test
    void queueStatusShouldReturnWaiting_whenPosition2() {
        ChargingOrder order = createOrder(OrderStatus.WAITING);
        order.setRequestMode(RequestMode.FAST);
        doReturn(order).when(chargingOrderMapper).selectOne(any());
        doReturn(2).when(engine).waitPosition(PileType.FAST, order.getOrderId());

        var result = service.queueStatus(CAR_ID);
        assertEquals(3, result.getData().getQueueNum());
        assertEquals(2, result.getData().getCarNumberBeforePosition());
    }

    @Test
    void queueStatusShouldReturnWaiting_whenNotFoundInQueue() {
        ChargingOrder order = createOrder(OrderStatus.WAITING);
        order.setRequestMode(RequestMode.FAST);
        doReturn(order).when(chargingOrderMapper).selectOne(any());
        doReturn(-1).when(engine).waitPosition(PileType.FAST, order.getOrderId());

        var result = service.queueStatus(CAR_ID);
        assertEquals(1, result.getData().getQueueNum());
        assertEquals(0, result.getData().getCarNumberBeforePosition());
    }

    @Test
    void queueStatusShouldReturnCalled_whenPosition0() {
        ChargingOrder order = createOrder(OrderStatus.CALLED);
        order.setPileId("F1");
        doReturn(order).when(chargingOrderMapper).selectOne(any());
        doReturn(0).when(engine).pilePosition("F1", order.getOrderId());

        var result = service.queueStatus(CAR_ID);
        assertEquals("called", result.getData().getCarState());
        assertEquals(1, result.getData().getQueueNum());
        assertEquals(0, result.getData().getCarNumberBeforePosition());
        assertEquals("F1", result.getData().getAssignedPileNum());
    }

    @Test
    void queueStatusShouldReturnCalled_whenPosition1() {
        ChargingOrder order = createOrder(OrderStatus.CALLED);
        order.setPileId("F1");
        doReturn(order).when(chargingOrderMapper).selectOne(any());
        doReturn(1).when(engine).pilePosition("F1", order.getOrderId());

        var result = service.queueStatus(CAR_ID);
        assertEquals(2, result.getData().getQueueNum());
        assertEquals(1, result.getData().getCarNumberBeforePosition());
    }

    @Test
    void queueStatusShouldReturnCalled_whenNotFoundInQueue() {
        ChargingOrder order = createOrder(OrderStatus.CALLED);
        order.setPileId("F2");
        doReturn(order).when(chargingOrderMapper).selectOne(any());
        doReturn(-1).when(engine).pilePosition("F2", order.getOrderId());

        var result = service.queueStatus(CAR_ID);
        assertEquals(1, result.getData().getQueueNum());
        assertEquals(0, result.getData().getCarNumberBeforePosition());
    }

    @Test
    void queueStatusShouldReturnCharging() {
        ChargingOrder order = createOrder(OrderStatus.CHARGING);
        order.setPileId("F1");
        doReturn(order).when(chargingOrderMapper).selectOne(any());

        var result = service.queueStatus(CAR_ID);
        assertEquals("charging", result.getData().getCarState());
        assertEquals(0, result.getData().getQueueNum());
        assertEquals(0, result.getData().getCarNumberBeforePosition());
        assertEquals("F1", result.getData().getAssignedPileNum());
    }

    @Test
    void queueStatusShouldReturnDone() {
        ChargingOrder order = createOrder(OrderStatus.FINISHED);
        doReturn(order).when(chargingOrderMapper).selectOne(any());

        var result = service.queueStatus(CAR_ID);
        assertEquals("done", result.getData().getCarState());
        assertEquals(0, result.getData().getQueueNum());
        assertEquals(0, result.getData().getCarNumberBeforePosition());
    }

    @Test
    void queueStatusShouldFormatRequestTime() {
        ChargingOrder order = createOrder(OrderStatus.WAITING);
        order.setRequestMode(RequestMode.FAST);
        order.setCreatedAt(LocalDateTime.of(2026, 6, 15, 14, 30, 0));
        doReturn(order).when(chargingOrderMapper).selectOne(any());
        doReturn(0).when(engine).waitPosition(PileType.FAST, order.getOrderId());

        var result = service.queueStatus(CAR_ID);
        assertEquals("2026-06-15 14:30:00", result.getData().getRequestTime());
    }

    // ========== 查看充电状态 ==========

    @Test
    void stateShouldReturn400_whenCarIdIsEmpty() {
        assertEquals(400, service.chargingState("").getCode());
    }

    @Test
    void stateShouldReturnNone_whenNoSession() {
        doReturn(null).when(chargingSessionMapper).selectOne(any());
        var result = service.chargingState(CAR_ID);
        assertEquals(200, result.getCode());
        assertEquals("none", result.getData().getStatus());
    }

    @Test
    void stateShouldReturnCharging() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 16, 14, 0);
        LocalDateTime now = start.plusHours(1);

        ChargingSession session = createSession(start, 40, SessionStatus.CHARGING);
        ChargingPile pile = createFastPile("F1");
        pile.setPowerState(PowerState.ON);

        doReturn(session).when(chargingSessionMapper).selectOne(any());
        doReturn(pile).when(chargingPileMapper).selectById("F1");
        doReturn(now).when(timeProvider).now();
        doReturn(mutableList(period("00:00", "24:00", "1.5", "5.0"))).when(billingRatePeriodMapper).selectList(any());

        var result = service.chargingState(CAR_ID);
        assertEquals(200, result.getCode());
        assertEquals("charging", result.getData().getStatus());
        assertEquals("30.00", result.getData().getCurrentAmount().toString());
        assertEquals("45.00", result.getData().getCurrentChargeFee().toString());
        assertEquals("150.00", result.getData().getCurrentServiceFee().toString());
        assertEquals("195.00", result.getData().getTotalCurrentFee().toString());
        assertEquals("01:00:00", result.getData().getCurrentDuration());
        assertEquals("1.5", result.getData().getCurrentPeriodPrice().toString());
    }

    @Test
    void stateShouldReturnCharging_whenCrossPeriods() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 16, 14, 0);
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 16, 0);

        ChargingSession session = createSession(start, 60, SessionStatus.CHARGING);
        ChargingPile pile = createFastPile("F1");
        pile.setPowerState(PowerState.ON);

        doReturn(session).when(chargingSessionMapper).selectOne(any());
        doReturn(pile).when(chargingPileMapper).selectById("F1");
        doReturn(now).when(timeProvider).now();
        doReturn(mutableList(
                period("08:00", "15:00", "1.5", "5.0"),
                period("15:00", "22:00", "1.0", "5.0")
        )).when(billingRatePeriodMapper).selectList(any());

        var result = service.chargingState(CAR_ID);
        // 14:00-15:00: 30kWh × (1.5+5.0) = 195
        // 15:00-16:00: 30kWh × (1.0+5.0) = 180
        // total: 60kWh, chargeFee=45+30=75, serviceFee=150+150=300
        assertEquals("60.00", result.getData().getCurrentAmount().toString());
        assertEquals("75.00", result.getData().getCurrentChargeFee().toString());
        assertEquals("300.00", result.getData().getCurrentServiceFee().toString());
        assertEquals("375.00", result.getData().getTotalCurrentFee().toString());
        assertEquals("1.0", result.getData().getCurrentPeriodPrice().toString());
    }

    @Test
    void stateShouldCapAmount_whenExceedsTarget() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 16, 14, 0);
        LocalDateTime now = start.plusHours(3);

        ChargingSession session = createSession(start, 40, SessionStatus.CHARGING);
        ChargingPile pile = createFastPile("F1");
        pile.setPowerState(PowerState.ON);

        doReturn(session).when(chargingSessionMapper).selectOne(any());
        doReturn(pile).when(chargingPileMapper).selectById("F1");
        doReturn(now).when(timeProvider).now();
        doReturn(mutableList(period("00:00", "24:00", "1.5", "5.0"))).when(billingRatePeriodMapper).selectList(any());

        var result = service.chargingState(CAR_ID);
        assertEquals("40.00", result.getData().getCurrentAmount().toString()); // cap at target
    }

    @Test
    void stateShouldReturnNone_whenSessionNotCharging() {
        doReturn(null).when(chargingSessionMapper).selectOne(any());
        assertEquals("none", service.chargingState(CAR_ID).getData().getStatus());
    }

    // ========== 结束充电 ==========

    @Test
    void endShouldReturn400_whenCarIdIsEmpty() {
        ChargingEndRequest req = new ChargingEndRequest();
        req.setChargingPileNum("F1");
        assertEquals(400, service.end(req).getCode());
    }

    @Test
    void endShouldReturn400_whenPileIdIsEmpty() {
        ChargingEndRequest req = new ChargingEndRequest();
        req.setCarId(CAR_ID);
        assertEquals(400, service.end(req).getCode());
    }

    @Test
    void endShouldReturn400_whenNoChargingSession() {
        ChargingEndRequest req = new ChargingEndRequest();
        req.setCarId(CAR_ID);
        req.setChargingPileNum("F1");
        doReturn(null).when(chargingSessionMapper).selectOne(any());
        assertEquals(400, service.end(req).getCode());
    }

    @Test
    void endShouldReturn404_whenOrderNotFound() {
        ChargingSession session = createSession(NOW, 40, SessionStatus.CHARGING);
        ChargingEndRequest req = new ChargingEndRequest();
        req.setCarId(CAR_ID);
        req.setChargingPileNum("F1");
        doReturn(session).when(chargingSessionMapper).selectOne(any());
        doReturn(null).when(chargingOrderMapper).selectById(any());
        assertEquals(404, service.end(req).getCode());
    }

    @Test
    void endShouldReturn404_whenPileNotFound() {
        ChargingSession session = createSession(NOW, 40, SessionStatus.CHARGING);
        ChargingEndRequest req = new ChargingEndRequest();
        req.setCarId(CAR_ID);
        req.setChargingPileNum("F1");
        doReturn(session).when(chargingSessionMapper).selectOne(any());
        doReturn(createCalledOrder()).when(chargingOrderMapper).selectById(any());
        doReturn(null).when(chargingPileMapper).selectById("F1");
        assertEquals(404, service.end(req).getCode());
    }

    @Test
    void endShouldCompleteCharging() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 16, 14, 0);
        LocalDateTime end = start.plusHours(1);
        ChargingSession session = createSession(start, 40, SessionStatus.CHARGING);
        ChargingOrder order = createCalledOrder();
        ChargingPile pile = createFastPile("F1");
        pile.setPowerState(PowerState.ON);

        ChargingEndRequest req = new ChargingEndRequest();
        req.setCarId(CAR_ID);
        req.setChargingPileNum("F1");

        doReturn(session).when(chargingSessionMapper).selectOne(any());
        doReturn(order).when(chargingOrderMapper).selectById(any());
        doReturn(pile).when(chargingPileMapper).selectById("F1");
        doReturn(end).when(timeProvider).now();
        doReturn(mutableList(period("00:00", "24:00", "1.5", "5.0"))).when(billingRatePeriodMapper).selectList(any());
        // onPileReleased 默认 mock 返回空，无需配置

        var result = service.end(req);
        assertEquals(200, result.getCode());
        assertEquals(Integer.valueOf(1), result.getData().getResult());

        verify(billMapper).insert(any(Bill.class));
        verify(chargingSessionMapper).updateById(any(ChargingSession.class));
        verify(chargingOrderMapper).updateById(any(ChargingOrder.class));
        verify(chargingPileMapper).updateById(any(ChargingPile.class));
        verify(engine).onPileReleased(eq("F1"), any());
    }

    @Test
    void endShouldThrow_whenCalledTwice() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 16, 14, 0);
        ChargingSession session = createSession(start, 40, SessionStatus.CHARGING);
        ChargingOrder order = createCalledOrder();
        ChargingPile pile = createFastPile("F1");
        pile.setPowerState(PowerState.ON);

        ChargingEndRequest req = new ChargingEndRequest();
        req.setCarId(CAR_ID);
        req.setChargingPileNum("F1");

        doReturn(session).when(chargingSessionMapper).selectOne(any());
        doReturn(order).when(chargingOrderMapper).selectById(any());
        doReturn(pile).when(chargingPileMapper).selectById("F1");

        LocalDateTime end = start.plusHours(1);
        doReturn(end).when(timeProvider).now();
        doReturn(mutableList(period("00:00", "24:00", "1.5", "5.0"))).when(billingRatePeriodMapper).selectList(any());
        // onPileReleased 默认 mock 返回空，无需配置
        service.end(req);

        doReturn(null).when(chargingSessionMapper).selectOne(any());
        assertEquals(400, service.end(req).getCode());
    }

    @Test
    void endShouldFillFromWaiting_whenWaitingCarExists() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 16, 14, 0);
        LocalDateTime end = start.plusHours(1);
        ChargingSession session = createSession(start, 40, SessionStatus.CHARGING);
        ChargingOrder order = createCalledOrder();
        ChargingPile pile = createFastPile("F1");
        pile.setPowerState(PowerState.ON);

        ChargingOrder waitingOrder = createOrder(OrderStatus.WAITING);
        waitingOrder.setOrderId("wait-order-1");
        waitingOrder.setRequestMode(RequestMode.FAST);
        QueueEntry waitEntry = new QueueEntry();
        waitEntry.setId(1L);
        waitEntry.setOrderId("wait-order-1");

        ChargingEndRequest req = new ChargingEndRequest();
        req.setCarId(CAR_ID);
        req.setChargingPileNum("F1");

        doReturn(session).when(chargingSessionMapper).selectOne(any());
        doReturn(order).when(chargingOrderMapper).selectById(any());
        doReturn(pile).when(chargingPileMapper).selectById("F1");
        doReturn(end).when(timeProvider).now();
        doReturn(mutableList(period("00:00", "24:00", "1.5", "5.0"))).when(billingRatePeriodMapper).selectList(any());
        doReturn(false).when(engine).hasAnyFault();
        doReturn(waitEntry).when(queueEntryMapper).selectOne(any());
        doReturn(waitingOrder).when(chargingOrderMapper).selectById("wait-order-1");
        doReturn(true).when(engine).addToPileQueue("F1", waitingOrder);
        // onPileReleased 默认 mock 返回空，无需配置

        service.end(req);
        verify(engine).addToPileQueue(eq("F1"), eq(waitingOrder));
        verify(chargingOrderMapper, atLeastOnce()).updateById(any(ChargingOrder.class));
    }

    @Test
    void endShouldNotFillFromWaiting_whenFaultExists() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 16, 14, 0);
        LocalDateTime end = start.plusHours(1);
        ChargingSession session = createSession(start, 40, SessionStatus.CHARGING);
        ChargingOrder order = createCalledOrder();
        ChargingPile pile = createFastPile("F1");
        pile.setPowerState(PowerState.ON);

        ChargingEndRequest req = new ChargingEndRequest();
        req.setCarId(CAR_ID);
        req.setChargingPileNum("F1");

        doReturn(session).when(chargingSessionMapper).selectOne(any());
        doReturn(order).when(chargingOrderMapper).selectById(any());
        doReturn(pile).when(chargingPileMapper).selectById("F1");
        doReturn(end).when(timeProvider).now();
        doReturn(mutableList(period("00:00", "24:00", "1.5", "5.0"))).when(billingRatePeriodMapper).selectList(any());
        doReturn(true).when(engine).hasAnyFault();
        // onPileReleased 默认 mock 返回空，无需配置

        service.end(req);
        // tryFillFromWaiting 跳过，不额外调用 deleteById
        verify(queueEntryMapper, atMostOnce()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void endShouldRemoveStaleWaitingEntry() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 16, 14, 0);
        LocalDateTime end = start.plusHours(1);
        ChargingSession session = createSession(start, 40, SessionStatus.CHARGING);
        ChargingOrder order = createCalledOrder();
        ChargingPile pile = createFastPile("F1");
        pile.setPowerState(PowerState.ON);

        ChargingOrder finishedOrder = createOrder(OrderStatus.FINISHED);
        finishedOrder.setOrderId("stale-order-1");
        QueueEntry staleEntry = new QueueEntry();
        staleEntry.setId(99L);
        staleEntry.setOrderId("stale-order-1");

        ChargingEndRequest req = new ChargingEndRequest();
        req.setCarId(CAR_ID);
        req.setChargingPileNum("F1");

        doReturn(session).when(chargingSessionMapper).selectOne(any());
        doReturn(order).when(chargingOrderMapper).selectById(any());
        doReturn(pile).when(chargingPileMapper).selectById("F1");
        doReturn(end).when(timeProvider).now();
        doReturn(mutableList(period("00:00", "24:00", "1.5", "5.0"))).when(billingRatePeriodMapper).selectList(any());
        doReturn(false).when(engine).hasAnyFault();
        doReturn(staleEntry).doReturn(null).when(queueEntryMapper).selectOne(any());
        doReturn(finishedOrder).when(chargingOrderMapper).selectById("stale-order-1");

        service.end(req);
        verify(queueEntryMapper).deleteById(99L);
    }

    // ========== 取消充电申请 ==========

    @Test
    void cancelShouldReturn400_whenCarIdIsEmpty() {
        assertEquals(400, service.cancel(new ChargingCancelRequest()).getCode());
    }

    @Test
    void cancelShouldReturn404_whenNoCancelableOrder() {
        // 无 WAITING/CALLED 订单（FINISHED/CHARGING/CANCELLED 时查不到）
        doReturn(null).when(chargingOrderMapper).selectOne(any());
        ChargingCancelRequest req = new ChargingCancelRequest();
        req.setCarId(CAR_ID);
        assertEquals(404, service.cancel(req).getCode());
    }

    @Test
    void cancelShouldRemoveFromWait_whenWaiting() {
        ChargingOrder order = createOrder(OrderStatus.WAITING);
        order.setRequestMode(RequestMode.FAST);
        doReturn(order).when(chargingOrderMapper).selectOne(any());

        ChargingCancelRequest req = new ChargingCancelRequest();
        req.setCarId(CAR_ID);
        assertEquals(200, service.cancel(req).getCode());

        verify(engine).removeFromWait(CAR_ID);
        verify(queueEntryMapper).delete(any());
        verify(chargingOrderMapper).updateById(any(ChargingOrder.class));
    }

    @Test
    void cancelShouldRemoveFromPileQueue_whenCalled() {
        ChargingOrder order = createOrder(OrderStatus.CALLED);
        order.setPileId("F1");
        doReturn(order).when(chargingOrderMapper).selectOne(any());

        ChargingCancelRequest req = new ChargingCancelRequest();
        req.setCarId(CAR_ID);
        assertEquals(200, service.cancel(req).getCode());

        verify(engine).removeFromAllPileQueues(CAR_ID);
        verify(queueEntryMapper).delete(any());
        verify(chargingOrderMapper).updateById(any(ChargingOrder.class));
    }

    @Test
    void cancelShouldReturn404_whenOrderNotWaitingOrCalled() {
        doReturn(null).when(chargingOrderMapper).selectOne(any());

        ChargingCancelRequest req = new ChargingCancelRequest();
        req.setCarId(CAR_ID);
        assertEquals(404, service.cancel(req).getCode());
    }

    // ========== Helper ==========

    @SafeVarargs
    private static <T> List<T> mutableList(T... items) {
        return new ArrayList<>(List.of(items));
    }

    private static ChargingOrder createCalledOrder() {
        return createOrder(OrderStatus.CALLED);
    }

    private static ChargingOrder createOrder(OrderStatus status) {
        ChargingOrder o = new ChargingOrder();
        o.setOrderId("order-1");
        o.setCarId(CAR_ID);
        o.setOrderStatus(status);
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

    private static ChargingSession createSession(LocalDateTime start, int targetKwh, SessionStatus status) {
        ChargingSession s = new ChargingSession();
        s.setSessionId("session-1");
        s.setOrderId("order-1");
        s.setPileId("F1");
        s.setCarId(CAR_ID);
        s.setStartTime(start);
        s.setTargetKwh(BigDecimal.valueOf(targetKwh));
        s.setChargedKwh(BigDecimal.ZERO);
        s.setSessionStatus(status);
        return s;
    }

    private static BillingRatePeriod period(String start, String end, String elec, String svc) {
        BillingRatePeriod p = new BillingRatePeriod();
        p.setStartTime(start);
        p.setEndTime(end);
        p.setElectricityPrice(new BigDecimal(elec));
        p.setServicePrice(new BigDecimal(svc));
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

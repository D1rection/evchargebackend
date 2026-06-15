package bupt.evchargebackend.service.pile;

import bupt.evchargebackend.dto.pile.PileQueueItem;
import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.charging.ChargingSession;
import bupt.evchargebackend.entity.charging.enums.OrderStatus;
import bupt.evchargebackend.entity.charging.enums.SessionStatus;
import bupt.evchargebackend.entity.pile.ChargingPile;
import bupt.evchargebackend.entity.pile.enums.PileType;
import bupt.evchargebackend.entity.pile.enums.PowerState;
import bupt.evchargebackend.entity.pile.enums.WorkingState;
import bupt.evchargebackend.entity.user.Car;
import bupt.evchargebackend.mapper.charging.ChargingSessionMapper;
import bupt.evchargebackend.mapper.pile.ChargingPileMapper;
import bupt.evchargebackend.mapper.user.CarMapper;
import bupt.evchargebackend.service.pile.impl.PileServiceImpl;
import bupt.evchargebackend.service.schedule.SchedulingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class PileServiceImplTest {

    private ChargingPileMapper chargingPileMapper;
    private SchedulingEngine engine;
    private ChargingSessionMapper chargingSessionMapper;
    private CarMapper carMapper;
    private PileServiceImpl service;

    private static final String PILE_ID = "F1";
    private static final String CAR_ID = "C001";

    @BeforeEach
    void setUp() {
        chargingPileMapper = mock(ChargingPileMapper.class);
        engine = mock(SchedulingEngine.class);
        chargingSessionMapper = mock(ChargingSessionMapper.class);
        carMapper = mock(CarMapper.class);
        service = new PileServiceImpl(chargingPileMapper, engine, chargingSessionMapper, carMapper);
    }

    @Test
    void shouldReturn404_whenPileNotFound() {
        doReturn(null).when(chargingPileMapper).selectById(PILE_ID);
        assertEquals(404, service.getPileQueue(PILE_ID).getCode());
    }

    @Test
    void shouldReturnEmpty_whenQueueEmpty() {
        doReturn(createPile()).when(chargingPileMapper).selectById(PILE_ID);
        doReturn(null).when(engine).peekPileQueue(PILE_ID);
        doReturn(List.of()).when(engine).getPileQueue(PILE_ID);

        var result = service.getPileQueue(PILE_ID);
        assertEquals(200, result.getCode());
        assertTrue(result.getData().isEmpty());
    }

    @Test
    void shouldReturnOneItem_whenOnlyChargingCar() {
        ChargingOrder order = createOrder(CAR_ID, BigDecimal.valueOf(30), OrderStatus.CHARGING);
        ChargingPile pile = createPile(PowerState.ON, WorkingState.CHARGING);
        ChargingSession session = createSession(BigDecimal.valueOf(30), BigDecimal.valueOf(10));
        Car car = createCar(CAR_ID, BigDecimal.valueOf(60));

        doReturn(pile).when(chargingPileMapper).selectById(PILE_ID);
        doReturn(order).when(engine).peekPileQueue(PILE_ID);
        doReturn(List.of()).when(engine).getPileQueue(PILE_ID);
        doReturn(session).when(chargingSessionMapper).selectOne(any());
        doReturn(car).when(carMapper).selectById(CAR_ID);

        List<PileQueueItem> items = service.getPileQueue(PILE_ID).getData();
        assertEquals(1, items.size());
        assertEquals(CAR_ID, items.get(0).getCarId());
        assertEquals("00:40:00", items.get(0).getWaitTime()); // (30-10)/30*3600=2400s=40min
        assertEquals(0, items.get(0).getQueuePosition());
        assertEquals(BigDecimal.valueOf(30), items.get(0).getRequestAmount());
        assertEquals(BigDecimal.valueOf(60), items.get(0).getCarCapacity());
    }

    @Test
    void shouldReturnMultipleItems_whenQueueHasWaitingCars() {
        ChargingOrder headOrder = createOrder("C001", BigDecimal.valueOf(30), OrderStatus.CHARGING);
        ChargingOrder wait1 = createOrder("C002", BigDecimal.valueOf(20), OrderStatus.CALLED);
        ChargingOrder wait2 = createOrder("C003", BigDecimal.valueOf(10), OrderStatus.CALLED);
        ChargingPile pile = createPile(PowerState.ON, WorkingState.CHARGING);
        ChargingSession session = createSession(BigDecimal.valueOf(30), BigDecimal.valueOf(15));
        Car car1 = createCar("C001", BigDecimal.valueOf(60));
        Car car2 = createCar("C002", BigDecimal.valueOf(50));
        Car car3 = createCar("C003", BigDecimal.valueOf(40));

        doReturn(pile).when(chargingPileMapper).selectById(PILE_ID);
        doReturn(headOrder).when(engine).peekPileQueue(PILE_ID);
        doReturn(List.of(wait1, wait2)).when(engine).getPileQueue(PILE_ID);
        doReturn(session).when(chargingSessionMapper).selectOne(any());
        doReturn(car1).when(carMapper).selectById("C001");
        doReturn(car2).when(carMapper).selectById("C002");
        doReturn(car3).when(carMapper).selectById("C003");

        List<PileQueueItem> items = service.getPileQueue(PILE_ID).getData();
        assertEquals(3, items.size());

        // position 0 (charging): remaining (30-15)/30*3600=1800s=30min
        assertEquals("C001", items.get(0).getCarId());
        assertEquals("00:30:00", items.get(0).getWaitTime());
        assertEquals(0, items.get(0).getQueuePosition());

        // position 1: wait 30min
        assertEquals("C002", items.get(1).getCarId());
        assertEquals("00:30:00", items.get(1).getWaitTime());
        assertEquals(1, items.get(1).getQueuePosition());

        // position 2: 30min + 20/30*3600=30+40=70min
        assertEquals("C003", items.get(2).getCarId());
        assertEquals("01:10:00", items.get(2).getWaitTime());
        assertEquals(2, items.get(2).getQueuePosition());
    }

    @Test
    void shouldReturnZeroWaitTime_whenSessionFullyCharged() {
        ChargingOrder order = createOrder(CAR_ID, BigDecimal.valueOf(30), OrderStatus.CHARGING);
        ChargingPile pile = createPile(PowerState.ON, WorkingState.CHARGING);
        ChargingSession session = createSession(BigDecimal.valueOf(30), BigDecimal.valueOf(30)); // fully charged
        Car car = createCar(CAR_ID, BigDecimal.valueOf(60));

        doReturn(pile).when(chargingPileMapper).selectById(PILE_ID);
        doReturn(order).when(engine).peekPileQueue(PILE_ID);
        doReturn(List.of()).when(engine).getPileQueue(PILE_ID);
        doReturn(session).when(chargingSessionMapper).selectOne(any());
        doReturn(car).when(carMapper).selectById(CAR_ID);

        List<PileQueueItem> items = service.getPileQueue(PILE_ID).getData();
        assertEquals("00:00:00", items.get(0).getWaitTime());
    }

    @Test
    void shouldSkipHead_whenCarNotFound() {
        ChargingOrder order = createOrder(CAR_ID, BigDecimal.valueOf(30), OrderStatus.CHARGING);
        ChargingPile pile = createPile(PowerState.ON, WorkingState.CHARGING);

        doReturn(pile).when(chargingPileMapper).selectById(PILE_ID);
        doReturn(order).when(engine).peekPileQueue(PILE_ID);
        doReturn(List.of()).when(engine).getPileQueue(PILE_ID);
        doReturn(null).when(carMapper).selectById(CAR_ID); // car not found

        List<PileQueueItem> items = service.getPileQueue(PILE_ID).getData();
        assertTrue(items.isEmpty());
    }

    @Test
    void shouldShowWaitingCars_whenHeadIsNull() {
        ChargingOrder wait1 = createOrder("C002", BigDecimal.valueOf(30), OrderStatus.CALLED);
        ChargingPile pile = createPile(PowerState.ON, WorkingState.CHARGING);
        Car car = createCar("C002", BigDecimal.valueOf(60));

        doReturn(pile).when(chargingPileMapper).selectById(PILE_ID);
        doReturn(null).when(engine).peekPileQueue(PILE_ID);
        doReturn(List.of(wait1)).when(engine).getPileQueue(PILE_ID);
        doReturn(car).when(carMapper).selectById("C002");

        List<PileQueueItem> items = service.getPileQueue(PILE_ID).getData();
        assertEquals(1, items.size());
        assertEquals("C002", items.get(0).getCarId());
        assertEquals(0, items.get(0).getQueuePosition()); // starts from 0
        assertEquals("00:00:00", items.get(0).getWaitTime());
    }

    @Test
    void shouldSkipWaitingCar_whenCarNotFound() {
        ChargingOrder order = createOrder(CAR_ID, BigDecimal.valueOf(30), OrderStatus.CHARGING);
        ChargingOrder wait1 = createOrder("C002", BigDecimal.valueOf(20), OrderStatus.CALLED);
        ChargingOrder wait2 = createOrder("C003", BigDecimal.valueOf(10), OrderStatus.CALLED);
        ChargingPile pile = createPile(PowerState.ON, WorkingState.CHARGING);
        ChargingSession session = createSession(BigDecimal.valueOf(30), BigDecimal.valueOf(15));
        Car car1 = createCar(CAR_ID, BigDecimal.valueOf(60));
        Car car3 = createCar("C003", BigDecimal.valueOf(40));

        doReturn(pile).when(chargingPileMapper).selectById(PILE_ID);
        doReturn(order).when(engine).peekPileQueue(PILE_ID);
        doReturn(List.of(wait1, wait2)).when(engine).getPileQueue(PILE_ID);
        doReturn(session).when(chargingSessionMapper).selectOne(any());
        doReturn(car1).when(carMapper).selectById(CAR_ID);
        doReturn(null).when(carMapper).selectById("C002"); // car not found
        doReturn(car3).when(carMapper).selectById("C003");

        List<PileQueueItem> items = service.getPileQueue(PILE_ID).getData();
        assertEquals(2, items.size());
        assertEquals("C001", items.get(0).getCarId()); // head present
        assertEquals("C003", items.get(1).getCarId()); // C002 skipped
        assertEquals(2, items.get(1).getQueuePosition()); // C002 occupied position 1 but skipped
    }

    // ========== Helper ==========

    private static ChargingPile createPile() {
        return createPile(PowerState.ON, WorkingState.AVAILABLE);
    }

    private static ChargingPile createPile(PowerState powerState, WorkingState workingState) {
        ChargingPile p = new ChargingPile();
        p.setPileId(PILE_ID);
        p.setPileType(PileType.FAST);
        p.setPowerKw(30);
        p.setPowerState(powerState);
        p.setWorkingState(workingState);
        return p;
    }

    private static ChargingOrder createOrder(String carId, BigDecimal targetKwh, OrderStatus status) {
        ChargingOrder o = new ChargingOrder();
        o.setOrderId("order-" + carId);
        o.setCarId(carId);
        o.setTargetKwh(targetKwh);
        o.setOrderStatus(status);
        return o;
    }

    private static ChargingSession createSession(BigDecimal targetKwh, BigDecimal chargedKwh) {
        ChargingSession s = new ChargingSession();
        s.setTargetKwh(targetKwh);
        s.setChargedKwh(chargedKwh);
        s.setSessionStatus(SessionStatus.CHARGING);
        return s;
    }

    private static Car createCar(String carId, BigDecimal batteryCapacity) {
        Car c = new Car();
        c.setCarId(carId);
        c.setBatteryCapacityKwh(batteryCapacity);
        return c;
    }
}

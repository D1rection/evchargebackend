package bupt.evchargebackend.service.queue;

import bupt.evchargebackend.dto.queue.WaitingQueueItem;
import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.charging.enums.RequestMode;
import bupt.evchargebackend.entity.user.Car;
import bupt.evchargebackend.mapper.user.CarMapper;
import bupt.evchargebackend.service.queue.impl.QueueServiceImpl;
import bupt.evchargebackend.common.time.TimeProvider;
import bupt.evchargebackend.service.schedule.SchedulingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class QueueServiceImplTest {

    private SchedulingEngine engine;
    private CarMapper carMapper;
    private TimeProvider timeProvider;
    private QueueServiceImpl service;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 16, 14, 0);

    @BeforeEach
    void setUp() {
        engine = mock(SchedulingEngine.class);
        carMapper = mock(CarMapper.class);
        timeProvider = mock(TimeProvider.class);
        service = new QueueServiceImpl(engine, carMapper, timeProvider);
        doReturn(NOW).when(timeProvider).now();
    }

    @Test
    void shouldReturn400_whenModeIsNull() {
        assertEquals(400, service.getWaitingQueue(null).getCode());
    }

    @Test
    void shouldReturn400_whenModeIsEmpty() {
        assertEquals(400, service.getWaitingQueue("").getCode());
    }

    @Test
    void shouldReturn400_whenModeIsInvalid() {
        assertEquals(400, service.getWaitingQueue("invalid").getCode());
    }

    @Test
    void shouldReturnFastQueue() {
        Queue<ChargingOrder> q = new LinkedList<>();
        q.add(order("C001", RequestMode.FAST, NOW.minusMinutes(10)));
        q.add(order("C002", RequestMode.FAST, NOW.minusMinutes(5)));
        doReturn(q).when(engine).getFastWaitQueue();
        doReturn(new LinkedList<ChargingOrder>()).when(engine).getSlowWaitQueue();
        doReturn(car("C001", 60)).when(carMapper).selectById("C001");
        doReturn(car("C002", 80)).when(carMapper).selectById("C002");

        List<WaitingQueueItem> items = service.getWaitingQueue("fast").getData();
        assertEquals(2, items.size());
        assertEquals("C001", items.get(0).getCarId());
        assertEquals("00:10:00", items.get(0).getWaitTime());
        assertEquals("FAST", items.get(0).getRequestMode());
        assertEquals("C002", items.get(1).getCarId());
        assertEquals("00:05:00", items.get(1).getWaitTime());
    }

    @Test
    void shouldReturnSlowQueue() {
        Queue<ChargingOrder> q = new LinkedList<>();
        q.add(order("C003", RequestMode.SLOW, NOW.minusMinutes(20)));
        doReturn(new LinkedList<ChargingOrder>()).when(engine).getFastWaitQueue();
        doReturn(q).when(engine).getSlowWaitQueue();
        doReturn(car("C003", 50)).when(carMapper).selectById("C003");

        List<WaitingQueueItem> items = service.getWaitingQueue("slow").getData();
        assertEquals(1, items.size());
        assertEquals("C003", items.get(0).getCarId());
        assertEquals("SLOW", items.get(0).getRequestMode());
    }

    @Test
    void shouldReturnAllMergedByCreatedAt() {
        // fast: C002(09:55), C004(09:57)
        Queue<ChargingOrder> fast = new LinkedList<>();
        fast.add(order("C002", RequestMode.FAST, NOW.minusMinutes(5)));
        fast.add(order("C004", RequestMode.FAST, NOW.minusMinutes(3)));

        // slow: C001(09:50), C003(10:00)
        Queue<ChargingOrder> slow = new LinkedList<>();
        slow.add(order("C001", RequestMode.SLOW, NOW.minusMinutes(10)));
        slow.add(order("C003", RequestMode.SLOW, NOW));

        doReturn(fast).when(engine).getFastWaitQueue();
        doReturn(slow).when(engine).getSlowWaitQueue();
        doReturn(car("C001", 60)).when(carMapper).selectById("C001");
        doReturn(car("C002", 60)).when(carMapper).selectById("C002");
        doReturn(car("C003", 60)).when(carMapper).selectById("C003");
        doReturn(car("C004", 60)).when(carMapper).selectById("C004");

        List<WaitingQueueItem> items = service.getWaitingQueue("all").getData();
        assertEquals(4, items.size());
        // 顺序: C001(09:50), C002(09:55), C004(09:57), C003(10:00)
        assertEquals("C001", items.get(0).getCarId());
        assertEquals("C002", items.get(1).getCarId());
        assertEquals("C004", items.get(2).getCarId());
        assertEquals("C003", items.get(3).getCarId());
    }

    @Test
    void shouldReturnAll_whenFastIsEmpty() {
        doReturn(new LinkedList<ChargingOrder>()).when(engine).getFastWaitQueue();
        Queue<ChargingOrder> slow = new LinkedList<>();
        slow.add(order("C001", RequestMode.SLOW, NOW.minusMinutes(5)));
        doReturn(slow).when(engine).getSlowWaitQueue();
        doReturn(car("C001", 60)).when(carMapper).selectById("C001");

        List<WaitingQueueItem> items = service.getWaitingQueue("all").getData();
        assertEquals(1, items.size());
        assertEquals("C001", items.get(0).getCarId());
    }

    @Test
    void shouldReturnEmpty_whenQueuesAreEmpty() {
        doReturn(new LinkedList<ChargingOrder>()).when(engine).getFastWaitQueue();
        doReturn(new LinkedList<ChargingOrder>()).when(engine).getSlowWaitQueue();

        assertTrue(service.getWaitingQueue("fast").getData().isEmpty());
        assertTrue(service.getWaitingQueue("slow").getData().isEmpty());
        assertTrue(service.getWaitingQueue("all").getData().isEmpty());
    }

    @Test
    void shouldSkip_whenCarNotFound() {
        Queue<ChargingOrder> q = new LinkedList<>();
        q.add(order("C001", RequestMode.FAST, NOW.minusMinutes(5)));
        doReturn(q).when(engine).getFastWaitQueue();
        doReturn(new LinkedList<ChargingOrder>()).when(engine).getSlowWaitQueue();
        doReturn(null).when(carMapper).selectById("C001");

        List<WaitingQueueItem> items = service.getWaitingQueue("fast").getData();
        assertTrue(items.isEmpty());
    }

    @Test
    void shouldSupportLargeBatteryCapacity() {
        Queue<ChargingOrder> q = new LinkedList<>();
        q.add(order("C001", RequestMode.FAST, NOW.minusMinutes(30)));
        doReturn(q).when(engine).getFastWaitQueue();
        doReturn(new LinkedList<ChargingOrder>()).when(engine).getSlowWaitQueue();
        doReturn(car("C001", 200)).when(carMapper).selectById("C001");

        List<WaitingQueueItem> items = service.getWaitingQueue("fast").getData();
        assertEquals(new BigDecimal("200"), items.get(0).getCarCapacity());
        assertEquals("00:30:00", items.get(0).getWaitTime());
    }

    // ========== Helper ==========

    private static ChargingOrder order(String carId, RequestMode mode, LocalDateTime createdAt) {
        ChargingOrder o = new ChargingOrder();
        o.setCarId(carId);
        o.setRequestMode(mode);
        o.setTargetKwh(BigDecimal.valueOf(40));
        o.setCreatedAt(createdAt);
        return o;
    }

    private static Car car(String carId, int batteryCapacity) {
        Car c = new Car();
        c.setCarId(carId);
        c.setBatteryCapacityKwh(BigDecimal.valueOf(batteryCapacity));
        return c;
    }
}

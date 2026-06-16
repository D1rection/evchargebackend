package bupt.evchargebackend.config;

import bupt.evchargebackend.entity.bill.Bill;
import bupt.evchargebackend.entity.bill.enums.PaymentStatus;
import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.charging.ChargingSession;
import bupt.evchargebackend.entity.charging.enums.OrderStatus;
import bupt.evchargebackend.entity.charging.enums.RequestMode;
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
import bupt.evchargebackend.entity.station.StationConfig;
import bupt.evchargebackend.entity.user.Car;
import bupt.evchargebackend.entity.user.UserAccount;
import bupt.evchargebackend.entity.user.enums.UserRole;
import bupt.evchargebackend.mapper.bill.BillMapper;
import bupt.evchargebackend.mapper.charging.ChargingOrderMapper;
import bupt.evchargebackend.mapper.charging.ChargingSessionMapper;
import bupt.evchargebackend.mapper.fault.FaultRecordMapper;
import bupt.evchargebackend.mapper.pile.ChargingPileMapper;
import bupt.evchargebackend.mapper.pricing.BillingRatePeriodMapper;
import bupt.evchargebackend.mapper.queue.QueueEntryMapper;
import bupt.evchargebackend.mapper.station.StationConfigMapper;
import bupt.evchargebackend.mapper.user.CarMapper;
import bupt.evchargebackend.mapper.user.UserAccountMapper;
import bupt.evchargebackend.service.schedule.SchedulingEngine;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final DataSource dataSource;
    private final UserAccountMapper userAccountMapper;
    private final CarMapper carMapper;
    private final ChargingPileMapper chargingPileMapper;
    private final StationConfigMapper stationConfigMapper;
    private final BillingRatePeriodMapper billingRatePeriodMapper;
    private final ChargingOrderMapper chargingOrderMapper;
    private final ChargingSessionMapper chargingSessionMapper;
    private final BillMapper billMapper;
    private final FaultRecordMapper faultRecordMapper;
    private final QueueEntryMapper queueEntryMapper;
    private final SchedulingEngine engine;

    @Value("${seed.force:false}")
    private boolean force;

    public DataSeeder(DataSource dataSource, UserAccountMapper userAccountMapper, CarMapper carMapper,
                      ChargingPileMapper chargingPileMapper,
                      StationConfigMapper stationConfigMapper,
                      BillingRatePeriodMapper billingRatePeriodMapper,
                      ChargingOrderMapper chargingOrderMapper,
                      ChargingSessionMapper chargingSessionMapper,
                      BillMapper billMapper, FaultRecordMapper faultRecordMapper,
                      QueueEntryMapper queueEntryMapper, SchedulingEngine engine) {
        this.dataSource = dataSource;
        this.userAccountMapper = userAccountMapper;
        this.carMapper = carMapper;
        this.chargingPileMapper = chargingPileMapper;
        this.stationConfigMapper = stationConfigMapper;
        this.billingRatePeriodMapper = billingRatePeriodMapper;
        this.chargingOrderMapper = chargingOrderMapper;
        this.chargingSessionMapper = chargingSessionMapper;
        this.billMapper = billMapper;
        this.faultRecordMapper = faultRecordMapper;
        this.queueEntryMapper = queueEntryMapper;
        this.engine = engine;
    }

    @Override
    public void run(String... args) {
        if (force) {
            log.warn("seed.force=true，清空所有表数据");
            truncateAll();
            engine.rebuild(List.of(), List.of(), List.of(), List.of(), Map.of());
        } else {
            Long count = userAccountMapper.selectCount(new QueryWrapper<>());
            if (count != null && count > 0) {
                log.info("数据库已有数据，跳过 seed（如需强制重置设置 seed.force=true）");
                return;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        log.info("===== 开始写入调试数据 =====");

        // 1. user_account
        insertUser("admin-001", "admin", "123456", UserRole.ADMIN);
        insertUser("user-001", "user1", "123456", UserRole.USER);
        insertUser("user-002", "user2", "123456", UserRole.USER);
        insertUser("user-003", "user3", "123456", UserRole.USER);
        insertUser("user-004", "user4", "123456", UserRole.USER);

        // 2. car
        insertCar("car-a", "user-001", "京A88881", new BigDecimal("60"));
        insertCar("car-b", "user-002", "京A88882", new BigDecimal("80"));
        insertCar("car-c", "user-003", "京A88883", new BigDecimal("50"));
        insertCar("car-d", "user-003", "京A88884", new BigDecimal("70"));
        insertCar("car-e", "user-001", "京A88885", new BigDecimal("60"));
        insertCar("car-finished", "user-004", "京A88886", new BigDecimal("60"));
        insertCar("car-cancelled", "user-004", "京A88887", new BigDecimal("60"));

        // 3. charging_pile
        insertPile("pile-fast-1", "FAST-01", PileType.FAST, 30, PowerState.ON, WorkingState.CHARGING, "session-active");
        insertPile("pile-fast-2", "FAST-02", PileType.FAST, 30, PowerState.ON, WorkingState.AVAILABLE, null);
        insertPile("pile-fast-3", "FAST-03", PileType.FAST, 30, PowerState.ON, WorkingState.FAULT, null);
        insertPile("pile-fast-4", "FAST-04", PileType.FAST, 30, PowerState.ON, WorkingState.AVAILABLE, null);
        insertPile("pile-slow-1", "SLOW-01", PileType.SLOW, 10, PowerState.ON, WorkingState.AVAILABLE, null);
        insertPile("pile-slow-2", "SLOW-02", PileType.SLOW, 10, PowerState.OFF, WorkingState.STOPPED, null);

        // 4. station_config
        StationConfig cfg = new StationConfig();
        cfg.setFastCount(4);
        cfg.setSlowCount(2);
        cfg.setWaitingSpotsPerPile(2);
        stationConfigMapper.insert(cfg);

        // 5. billing_rate_period
        insertRate("rp-f-peak",   PileType.FAST, PeriodName.PEAK,   "08:00", "12:00", "1.5", "0.8");
        insertRate("rp-f-norm",   PileType.FAST, PeriodName.NORMAL, "12:00", "18:00", "1.0", "0.5");
        insertRate("rp-f-valley", PileType.FAST, PeriodName.VALLEY, "18:00", "24:00", "0.6", "0.3");
        insertRate("rp-s-peak",   PileType.SLOW, PeriodName.PEAK,   "08:00", "12:00", "1.2", "0.6");
        insertRate("rp-s-norm",   PileType.SLOW, PeriodName.NORMAL, "12:00", "18:00", "0.8", "0.4");
        insertRate("rp-s-valley", PileType.SLOW, PeriodName.VALLEY, "18:00", "24:00", "0.5", "0.2");

        // 6. charging_order
        LocalDateTime t30 = now.minusMinutes(30);
        LocalDateTime t15 = now.minusMinutes(15);
        LocalDateTime t10 = now.minusMinutes(10);
        LocalDateTime t5  = now.minusMinutes(5);
        LocalDateTime t1h = now.minusHours(1);

        insertOrder("order-charging",  "ORD-CHG-001", "car-a", RequestMode.FAST, new BigDecimal("30"),
                    new BigDecimal("45.00"), 60, "pile-fast-1", OrderStatus.CHARGING, t30);
        insertOrder("order-fault",     "ORD-FLT-001", "car-b", RequestMode.FAST, new BigDecimal("20"),
                    null, null, "pile-fast-3", OrderStatus.CALLED, t15);
        insertOrder("order-called",    "ORD-CLD-001", "car-e", RequestMode.FAST, new BigDecimal("25"),
                    new BigDecimal("37.50"), 50, "pile-fast-2", OrderStatus.CALLED, t15);
        insertOrder("order-wait-1",    "ORD-WAIT-01", "car-c", RequestMode.FAST, new BigDecimal("40"),
                    new BigDecimal("60.00"), 80, null, OrderStatus.WAITING, t10);
        insertOrder("order-wait-2",    "ORD-WAIT-02", "car-d", RequestMode.FAST, new BigDecimal("35"),
                    new BigDecimal("52.50"), 70, null, OrderStatus.WAITING, t5);
        insertOrder("order-finished",  "ORD-FIN-001", "car-finished", RequestMode.SLOW, new BigDecimal("20"),
                    new BigDecimal("22.00"), 120, "pile-slow-1", OrderStatus.FINISHED, t1h);
        insertOrder("order-cancelled", "ORD-CAN-001", "car-cancelled", RequestMode.FAST, new BigDecimal("15"),
                    null, null, null, OrderStatus.CANCELLED, t1h);

        // 7. charging_session
        ChargingSession sess = new ChargingSession();
        sess.setSessionId("session-active");
        sess.setOrderId("order-charging");
        sess.setCarId("car-a");
        sess.setPileId("pile-fast-1");
        sess.setStartTime(t30);
        sess.setTargetKwh(new BigDecimal("30"));
        sess.setChargedKwh(BigDecimal.ZERO);
        sess.setSessionStatus(SessionStatus.CHARGING);
        chargingSessionMapper.insert(sess);

        // 8. bill
        Bill bill = new Bill();
        bill.setBillId("bill-finished");
        bill.setBillNo("BILL-FIN-001");
        bill.setOrderId("order-finished");
        bill.setCarId("car-finished");
        bill.setPileId("pile-slow-1");
        bill.setStartTime(t1h);
        bill.setEndTime(t1h.plusMinutes(120));
        bill.setChargedKwh(new BigDecimal("20.00"));
        bill.setChargeMinutes(120);
        bill.setElectricityFee(new BigDecimal("16.00"));
        bill.setServiceFee(new BigDecimal("6.00"));
        bill.setTotalFee(new BigDecimal("22.00"));
        bill.setPaymentStatus(PaymentStatus.UNPAID);
        billMapper.insert(bill);

        // 9. fault_record
        FaultRecord active = new FaultRecord();
        active.setFaultId("fault-active");
        active.setPileId("pile-fast-3");
        active.setFaultTime(t5);
        active.setFaultCode(101);
        active.setFaultStatus(FaultStatus.ACTIVE);
        active.setDescription("过流故障");
        faultRecordMapper.insert(active);

        FaultRecord recovered = new FaultRecord();
        recovered.setFaultId("fault-recovered");
        recovered.setPileId("pile-fast-1");
        recovered.setFaultTime(t1h);
        recovered.setRecoverTime(t1h.plusMinutes(10));
        recovered.setFaultCode(102);
        recovered.setFaultStatus(FaultStatus.RECOVERED);
        recovered.setResolveCode(200);
        recovered.setRemark("复位恢复");
        recovered.setDescription("过温故障");
        faultRecordMapper.insert(recovered);

        // 10. queue_entry
        queueEntryMapper.insert(entry("WAIT", "FAST", "order-wait-1"));
        queueEntryMapper.insert(entry("WAIT", "FAST", "order-wait-2"));
        queueEntryMapper.insert(entry("FAULT", "FAST", "order-fault"));
        queueEntryMapper.insert(entry("PILE", "pile-fast-1", "order-charging"));
        queueEntryMapper.insert(entry("PILE", "pile-fast-2", "order-called"));

        // 11. 重建引擎内存队列
        engine.rebuild(
                List.of(chargingOrderMapper.selectById("order-wait-1"),
                        chargingOrderMapper.selectById("order-wait-2")),
                List.of(),
                List.of(chargingOrderMapper.selectById("order-fault")),
                List.of(),
                Map.of("pile-fast-1", List.of(chargingOrderMapper.selectById("order-charging")),
                       "pile-fast-2", List.of(chargingOrderMapper.selectById("order-called")))
        );

        log.info("===== 调试数据写入完成 =====");
        log.info("账号: admin/123456, user1-4/123456 | 桩: FAST-01~04, SLOW-01~02");
    }

    private void insertUser(String id, String username, String password, UserRole role) {
        UserAccount u = new UserAccount();
        u.setUserId(id);
        u.setUsername(username);
        u.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        u.setRole(role);
        userAccountMapper.insert(u);
    }

    private void insertCar(String carId, String userId, String carNo, BigDecimal capacity) {
        Car c = new Car();
        c.setCarId(carId);
        c.setUserId(userId);
        c.setCarNo(carNo);
        c.setBatteryCapacityKwh(capacity);
        carMapper.insert(c);
    }

    private void insertPile(String id, String no, PileType type, int kw,
                            PowerState power, WorkingState work, String sessionId) {
        ChargingPile p = new ChargingPile();
        p.setPileId(id);
        p.setPileNo(no);
        p.setPileType(type);
        p.setPowerKw(kw);
        p.setPowerState(power);
        p.setWorkingState(work);
        p.setCurrentSessionId(sessionId);
        chargingPileMapper.insert(p);
    }

    private void insertRate(String id, PileType type, PeriodName name,
                            String start, String end, String elec, String svc) {
        BillingRatePeriod r = new BillingRatePeriod();
        r.setPeriodId(id);
        r.setPileType(type);
        r.setPeriodName(name);
        r.setStartTime(start);
        r.setEndTime(end);
        r.setElectricityPrice(new BigDecimal(elec));
        r.setServicePrice(new BigDecimal(svc));
        billingRatePeriodMapper.insert(r);
    }

    private void insertOrder(String id, String no, String carId, RequestMode mode,
                             BigDecimal target, BigDecimal fee, Integer minutes,
                             String pileId, OrderStatus status, LocalDateTime createdAt) {
        ChargingOrder o = new ChargingOrder();
        o.setOrderId(id);
        o.setOrderNo(no);
        o.setCarId(carId);
        o.setRequestMode(mode);
        o.setTargetKwh(target);
        o.setEstimatedFee(fee);
        o.setEstimatedMinutes(minutes);
        o.setPileId(pileId);
        o.setOrderStatus(status);
        o.setCreatedAt(createdAt);
        o.setUpdatedAt(createdAt);
        chargingOrderMapper.insert(o);
    }

    private static QueueEntry entry(String type, String key, String orderId) {
        QueueEntry e = new QueueEntry();
        e.setQueueType(type);
        e.setQueueKey(key);
        e.setOrderId(orderId);
        return e;
    }

    private void truncateAll() {
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
            throw new RuntimeException("清空数据失败", e);
        }
    }
}

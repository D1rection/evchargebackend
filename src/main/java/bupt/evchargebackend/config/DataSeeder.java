package bupt.evchargebackend.config;

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
import bupt.evchargebackend.mapper.pile.ChargingPileMapper;
import bupt.evchargebackend.mapper.pricing.BillingRatePeriodMapper;
import bupt.evchargebackend.mapper.station.StationConfigMapper;
import bupt.evchargebackend.mapper.user.CarMapper;
import bupt.evchargebackend.mapper.user.UserAccountMapper;
import bupt.evchargebackend.service.schedule.SchedulingEngine;
import cn.hutool.crypto.digest.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final DataSource dataSource;
    private final SchedulingEngine engine;
    private final UserAccountMapper userAccountMapper;
    private final CarMapper carMapper;
    private final ChargingPileMapper chargingPileMapper;
    private final BillingRatePeriodMapper billingRatePeriodMapper;
    private final StationConfigMapper stationConfigMapper;

    public DataSeeder(DataSource dataSource, SchedulingEngine engine,
                      UserAccountMapper userAccountMapper,
                      CarMapper carMapper,
                      ChargingPileMapper chargingPileMapper,
                      BillingRatePeriodMapper billingRatePeriodMapper,
                      StationConfigMapper stationConfigMapper) {
        this.dataSource = dataSource;
        this.engine = engine;
        this.userAccountMapper = userAccountMapper;
        this.carMapper = carMapper;
        this.chargingPileMapper = chargingPileMapper;
        this.billingRatePeriodMapper = billingRatePeriodMapper;
        this.stationConfigMapper = stationConfigMapper;
    }

    @Override
    public void run(String... args) {
        log.warn("清空所有数据库表数据");
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
        engine.rebuild(List.of(), List.of(), List.of(), List.of(), Map.of());

        log.info("初始化基础数据");
        initAccounts();
        initPiles();
        initPricing();
        initStationConfig();
        log.info("基础数据初始化完成");
    }

    private void initAccounts() {
        // 管理员
        UserAccount admin = new UserAccount();
        admin.setUserId("admin-1");
        admin.setUsername("admin");
        admin.setPasswordHash(BCrypt.hashpw("admin123", BCrypt.gensalt()));
        admin.setRole(UserRole.ADMIN);
        userAccountMapper.insert(admin);

        // 普通用户1
        UserAccount user1 = new UserAccount();
        user1.setUserId("user-1");
        user1.setUsername("user1");
        user1.setPasswordHash(BCrypt.hashpw("123456", BCrypt.gensalt()));
        user1.setRole(UserRole.USER);
        userAccountMapper.insert(user1);

        // 普通用户2
        UserAccount user2 = new UserAccount();
        user2.setUserId("user-2");
        user2.setUsername("user2");
        user2.setPasswordHash(BCrypt.hashpw("123456", BCrypt.gensalt()));
        user2.setRole(UserRole.USER);
        userAccountMapper.insert(user2);

        // user1 的车辆
        Car car1 = new Car();
        car1.setCarId("C001");
        car1.setUserId("user-1");
        car1.setCarNo("京A12345");
        car1.setBatteryCapacityKwh(BigDecimal.valueOf(60));
        carMapper.insert(car1);

        Car car2 = new Car();
        car2.setCarId("C002");
        car2.setUserId("user-1");
        car2.setCarNo("京A67890");
        car2.setBatteryCapacityKwh(BigDecimal.valueOf(80));
        carMapper.insert(car2);

        // user2 的车辆
        Car car3 = new Car();
        car3.setCarId("C003");
        car3.setUserId("user-2");
        car3.setCarNo("京B12345");
        car3.setBatteryCapacityKwh(BigDecimal.valueOf(40));
        carMapper.insert(car3);
    }

    private void initPiles() {
        // 快充桩
        addPile("F1", "快充01", PileType.FAST, 30);
        addPile("F2", "快充02", PileType.FAST, 30);

        // 慢充桩
        addPile("T1", "慢充01", PileType.SLOW, 10);
        addPile("T2", "慢充02", PileType.SLOW, 10);
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

    private void initPricing() {
        // 快充分时电价
        addRate(PileType.FAST, PeriodName.PEAK, "08:00", "15:00", "1.2", "0.3");
        addRate(PileType.FAST, PeriodName.NORMAL, "15:00", "22:00", "0.9", "0.3");
        addRate(PileType.FAST, PeriodName.VALLEY, "22:00", "08:00", "0.5", "0.3");

        // 慢充分时电价
        addRate(PileType.SLOW, PeriodName.PEAK, "08:00", "15:00", "1.0", "0.2");
        addRate(PileType.SLOW, PeriodName.NORMAL, "15:00", "22:00", "0.8", "0.2");
        addRate(PileType.SLOW, PeriodName.VALLEY, "22:00", "08:00", "0.4", "0.2");
    }

    private void addRate(PileType pileType, PeriodName name, String start, String end,
                         String elecPrice, String svcPrice) {
        BillingRatePeriod rate = new BillingRatePeriod();
        rate.setPeriodId(UUID.randomUUID().toString());
        rate.setPileType(pileType);
        rate.setPeriodName(name);
        rate.setStartTime(start);
        rate.setEndTime(end);
        rate.setElectricityPrice(new BigDecimal(elecPrice));
        rate.setServicePrice(new BigDecimal(svcPrice));
        billingRatePeriodMapper.insert(rate);
    }

    private void initStationConfig() {
        StationConfig config = new StationConfig();
        config.setFastCount(2);
        config.setSlowCount(2);
        config.setWaitingSpotsPerPile(3);
        stationConfigMapper.insert(config);
    }
}

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
        engine.rebuild(List.of(), List.of(), Map.of());

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

        // 普通用户1（V1~V15）
        UserAccount user1 = new UserAccount();
        user1.setUserId("user-1");
        user1.setUsername("user1");
        user1.setPasswordHash(BCrypt.hashpw("123456", BCrypt.gensalt()));
        user1.setRole(UserRole.USER);
        userAccountMapper.insert(user1);

        // 普通用户2（V16~V30）
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
    }

    private void initPiles() {
        // 快充桩（2台）
        addPile("F1", "快充01", PileType.FAST, 30);
        addPile("F2", "快充02", PileType.FAST, 30);

        // 慢充桩（3台）
        addPile("T1", "慢充01", PileType.SLOW, 10);
        addPile("T2", "慢充02", PileType.SLOW, 10);
        addPile("T3", "慢充03", PileType.SLOW, 10);
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
        // 分时电价（快慢同价），服务费 0.8 元/度
        // 峰时 1.0 元/度: 10:00~15:00, 18:00~21:00
        // 平时 0.7 元/度: 07:00~10:00, 15:00~18:00, 21:00~23:00
        // 谷时 0.4 元/度: 23:00~07:00
        addRate("10:00", "15:00", "1.0", "0.8");
        addRate("18:00", "21:00", "1.0", "0.8");
        addRate("07:00", "10:00", "0.7", "0.8");
        addRate("15:00", "18:00", "0.7", "0.8");
        addRate("21:00", "23:00", "0.7", "0.8");
        addRate("23:00", "07:00", "0.4", "0.8");
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

    private void initStationConfig() {
        StationConfig config = new StationConfig();
        config.setFastCount(2);
        config.setSlowCount(3);
        config.setWaitingSpotsPerPile(3);
        stationConfigMapper.insert(config);
    }
}

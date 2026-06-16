package bupt.evchargebackend.config;

import bupt.evchargebackend.service.schedule.SchedulingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final DataSource dataSource;
    private final SchedulingEngine engine;

    public DataSeeder(DataSource dataSource, SchedulingEngine engine) {
        this.dataSource = dataSource;
        this.engine = engine;
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
        log.info("数据库已清空");
    }
}

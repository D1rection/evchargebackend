package bupt.evchargebackend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 应用启动时自动创建缺失的数据库表（仅执行 CREATE TABLE IF NOT EXISTS）。
 * <p>
 * 替代 spring.sql.init.mode 方案，无需修改配置文件。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@Component
public class DatabaseInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final DataSource dataSource;

    public DatabaseInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("开始检查并创建数据库表...");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS station_config (
                    id                   INT         AUTO_INCREMENT PRIMARY KEY,
                    fast_count           INT         NOT NULL DEFAULT 0 COMMENT '快充桩数量',
                    slow_count           INT         NOT NULL DEFAULT 0 COMMENT '慢充桩数量',
                    waiting_spots_per_pile INT       NOT NULL DEFAULT 2 COMMENT '每桩等候车位',
                    updated_at           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """);

            log.info("数据库表检查完成");
        }
    }
}

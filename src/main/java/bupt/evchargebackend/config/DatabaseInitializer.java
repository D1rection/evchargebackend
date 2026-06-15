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

            // 注意：建表顺序需满足外键依赖，逐表 try-catch 防止单表失败影响后续表
            String[][] tables = {
                { "user_account", """
                    CREATE TABLE IF NOT EXISTS user_account (
                        user_id      VARCHAR(36)  PRIMARY KEY,
                        username     VARCHAR(50)  NOT NULL UNIQUE,
                        password_hash VARCHAR(255) NOT NULL,
                        role         VARCHAR(10)  NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
                        created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """ },
                { "car", """
                    CREATE TABLE IF NOT EXISTS car (
                        car_id              VARCHAR(36)  PRIMARY KEY,
                        user_id             VARCHAR(36)  NOT NULL,
                        car_no              VARCHAR(20)  NOT NULL,
                        battery_capacity_kwh DECIMAL(8,2) NOT NULL COMMENT 'kWh',
                        created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES user_account(user_id)
                    )
                    """ },
                { "charging_pile", """
                    CREATE TABLE IF NOT EXISTS charging_pile (
                        pile_id              VARCHAR(36)  PRIMARY KEY,
                        pile_no              VARCHAR(20)  NOT NULL COMMENT '对外展示',
                        pile_type            VARCHAR(10)  NOT NULL COMMENT 'FAST / SLOW',
                        power_kw             INT          NOT NULL COMMENT 'kW',
                        power_state          VARCHAR(10)  NOT NULL DEFAULT 'OFF' COMMENT 'OFF / ON',
                        working_state        VARCHAR(20)  NOT NULL DEFAULT 'STOPPED' COMMENT 'AVAILABLE / CHARGING / FAULT / STOPPED',
                        current_session_id   VARCHAR(36)  COMMENT '当前充电会话ID，仅逻辑关联',
                        total_charge_kwh     DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT 'kWh',
                        total_charge_count   INT          NOT NULL DEFAULT 0,
                        total_charge_minutes INT          NOT NULL DEFAULT 0 COMMENT '分钟',
                        created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """ },
                { "charging_order", """
                    CREATE TABLE IF NOT EXISTS charging_order (
                        order_id          VARCHAR(36)  PRIMARY KEY,
                        order_no          VARCHAR(50)  NOT NULL COMMENT '对外展示',
                        car_id            VARCHAR(36)  NOT NULL,
                        request_mode      VARCHAR(10)  NOT NULL COMMENT 'FAST / SLOW',
                        target_kwh        DECIMAL(8,2) NOT NULL COMMENT 'kWh',
                        estimated_fee     DECIMAL(8,2) COMMENT '元',
                        estimated_minutes INT          COMMENT '分钟',
                        order_status      VARCHAR(20)  NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING / CALLED / CHARGING / FINISHED / CANCELLED',
                        created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        FOREIGN KEY (car_id) REFERENCES car(car_id)
                    )
                    """ },
                { "charging_session", """
                    CREATE TABLE IF NOT EXISTS charging_session (
                        session_id      VARCHAR(36)  PRIMARY KEY,
                        order_id        VARCHAR(36)  NOT NULL,
                        car_id          VARCHAR(36)  NOT NULL,
                        pile_id         VARCHAR(36)  NOT NULL,
                        start_time      DATETIME,
                        end_time        DATETIME,
                        target_kwh      DECIMAL(8,2),
                        charged_kwh     DECIMAL(8,2) COMMENT 'kWh',
                        session_status  VARCHAR(20)  NOT NULL DEFAULT 'CHARGING' COMMENT 'CHARGING / FINISHED / INTERRUPTED',
                        created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        FOREIGN KEY (order_id) REFERENCES charging_order(order_id),
                        FOREIGN KEY (car_id) REFERENCES car(car_id),
                        FOREIGN KEY (pile_id) REFERENCES charging_pile(pile_id)
                    )
                    """ },
                { "bill", """
                    CREATE TABLE IF NOT EXISTS bill (
                        bill_id          VARCHAR(36)  PRIMARY KEY,
                        bill_no          VARCHAR(50)  NOT NULL COMMENT '对外展示',
                        order_id         VARCHAR(36)  NOT NULL,
                        session_id       VARCHAR(36),
                        car_id           VARCHAR(36)  NOT NULL,
                        pile_id          VARCHAR(36)  NOT NULL,
                        start_time       DATETIME     NOT NULL,
                        end_time         DATETIME     NOT NULL,
                        charged_kwh      DECIMAL(8,2) NOT NULL COMMENT 'kWh',
                        charge_minutes   INT          NOT NULL COMMENT '分钟',
                        electricity_fee  DECIMAL(8,2) NOT NULL COMMENT '元',
                        service_fee      DECIMAL(8,2) NOT NULL COMMENT '元',
                        total_fee        DECIMAL(8,2) NOT NULL COMMENT '元',
                        payment_status   VARCHAR(10)  NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID / PAID',
                        created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (order_id) REFERENCES charging_order(order_id),
                        FOREIGN KEY (session_id) REFERENCES charging_session(session_id),
                        FOREIGN KEY (car_id) REFERENCES car(car_id),
                        FOREIGN KEY (pile_id) REFERENCES charging_pile(pile_id)
                    )
                    """ },
                { "billing_rate_period", """
                    CREATE TABLE IF NOT EXISTS billing_rate_period (
                        period_id         VARCHAR(36)  PRIMARY KEY,
                        pile_type         VARCHAR(10)  NOT NULL COMMENT 'FAST / SLOW',
                        period_name       VARCHAR(10)  NOT NULL COMMENT 'PEAK / NORMAL / VALLEY',
                        start_time        VARCHAR(5)   NOT NULL COMMENT 'HH:mm',
                        end_time          VARCHAR(5)   NOT NULL COMMENT 'HH:mm',
                        electricity_price DECIMAL(6,4) NOT NULL COMMENT '元/kWh',
                        service_price     DECIMAL(6,4) NOT NULL COMMENT '元/kWh',
                        created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """ },
                { "fault_record", """
                    CREATE TABLE IF NOT EXISTS fault_record (
                        fault_id     VARCHAR(36)  PRIMARY KEY,
                        pile_id      VARCHAR(36)  NOT NULL,
                        session_id   VARCHAR(36)  COMMENT '可为空',
                        order_id     VARCHAR(36)  COMMENT '可为空',
                        fault_time   DATETIME     NOT NULL,
                        fault_code   INT          COMMENT '故障码: 101过流/102过温/103通信中断/404离线',
                        recover_time DATETIME,
                        fault_status VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / RECOVERED',
                        resolve_code INT          COMMENT '处置码: 200复位/201换硬件/202换通信/203重启/204其他',
                        resolver     VARCHAR(50)  COMMENT '处置人',
                        remark       TEXT         COMMENT '处置备注',
                        description  TEXT,
                        created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        FOREIGN KEY (pile_id) REFERENCES charging_pile(pile_id),
                        FOREIGN KEY (session_id) REFERENCES charging_session(session_id),
                        FOREIGN KEY (order_id) REFERENCES charging_order(order_id)
                    )
                    """ },
                { "station_config", """
                    CREATE TABLE IF NOT EXISTS station_config (
                        id                   INT         AUTO_INCREMENT PRIMARY KEY,
                        fast_count           INT         NOT NULL DEFAULT 0 COMMENT '快充桩数量',
                        slow_count           INT         NOT NULL DEFAULT 0 COMMENT '慢充桩数量',
                        waiting_spots_per_pile INT       NOT NULL DEFAULT 2 COMMENT '每桩等候车位',
                        updated_at           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """ },
            };

            for (String[] table : tables) {
                try {
                    stmt.execute(table[1]);
                    log.info("表 {} 就绪", table[0]);
                } catch (Exception e) {
                    log.error("创建表 {} 失败: {}", table[0], e.getMessage());
                }
            }

            // ========== Schema 演进：补全已存在表中缺失的列 ==========
            log.info("开始检查并补全缺失的列...");
            String[][] migrations = {
                { "fault_record", "fault_code", "INT COMMENT '故障码: 101过流/102过温/103通信中断/404离线'" },
                { "fault_record", "resolve_code", "INT COMMENT '处置码: 200复位/201换硬件/202换通信/203重启/204其他'" },
                { "fault_record", "resolver", "VARCHAR(50) COMMENT '处置人'" },
                { "fault_record", "remark", "TEXT COMMENT '处置备注'" },
                { "fault_record", "description", "TEXT COMMENT '故障描述'" },
                { "fault_record", "created_at", "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP" },
                { "fault_record", "updated_at", "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" },
            };
            for (String[] m : migrations) {
                try {
                    String checkSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
                    Integer count;
                    try (var ps = conn.prepareStatement(checkSql)) {
                        ps.setString(1, m[0]);
                        ps.setString(2, m[1]);
                        try (var rs = ps.executeQuery()) {
                            count = rs.next() ? rs.getInt(1) : 0;
                        }
                    }
                    if (count != null && count == 0) {
                        stmt.execute("ALTER TABLE " + m[0] + " ADD COLUMN " + m[1] + " " + m[2]);
                        log.info("列 {}.{} 已添加", m[0], m[1]);
                    } else {
                        log.info("列 {}.{} 已存在，跳过", m[0], m[1]);
                    }
                } catch (Exception e) {
                    log.error("添加列 {}.{} 失败: {}", m[0], m[1], e.getMessage());
                }
            }

            log.info("数据库表检查完成");
        }
    }
}

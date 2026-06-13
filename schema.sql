CREATE TABLE user_account (
    user_id      VARCHAR(36)  PRIMARY KEY,
    username     VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role         VARCHAR(10)  NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE car (
    car_id              VARCHAR(36)  PRIMARY KEY,
    user_id             VARCHAR(36)  NOT NULL,
    car_no              VARCHAR(20)  NOT NULL,
    battery_capacity_kwh DECIMAL(8,2) NOT NULL COMMENT 'kWh',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user_account(user_id)
);

CREATE TABLE charging_pile (
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
);

CREATE TABLE charging_order (
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
);

CREATE TABLE charging_session (
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
);

CREATE TABLE bill (
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
);

CREATE TABLE billing_rate_period (
    period_id         VARCHAR(36)  PRIMARY KEY,
    pile_type         VARCHAR(10)  NOT NULL COMMENT 'FAST / SLOW',
    period_name       VARCHAR(10)  NOT NULL COMMENT 'PEAK / NORMAL / VALLEY',
    start_time        VARCHAR(5)   NOT NULL COMMENT 'HH:mm',
    end_time          VARCHAR(5)   NOT NULL COMMENT 'HH:mm',
    electricity_price DECIMAL(6,4) NOT NULL COMMENT '元/kWh',
    service_price     DECIMAL(6,4) NOT NULL COMMENT '元/kWh',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE fault_record (
    fault_id     VARCHAR(36)  PRIMARY KEY,
    pile_id      VARCHAR(36)  NOT NULL,
    session_id   VARCHAR(36)  COMMENT '可为空',
    order_id     VARCHAR(36)  COMMENT '可为空',
    fault_time   DATETIME     NOT NULL,
    recover_time DATETIME,
    fault_status VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / RECOVERED',
    description  TEXT,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (pile_id) REFERENCES charging_pile(pile_id),
    FOREIGN KEY (session_id) REFERENCES charging_session(session_id),
    FOREIGN KEY (order_id) REFERENCES charging_order(order_id)
);

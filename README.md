# 波普特大学智能充电桩调度计费系统后端

软件工程课程大作业 — 智能充电桩调度计费系统的后端服务。

## 技术栈

| 技术 | 版本 |
|------|------|
| Java | 21 |
| Spring Boot | 4.1.0 |
| 构建工具 | Maven |
| 数据库 | MySQL 8.0 |
| ORM | MyBatis-Plus 3.5.16 |

### 已集成依赖

- **spring-boot-starter-webmvc** — Web MVC + Jackson 3 序列化
- **spring-boot-starter-validation** — 参数校验
- **mybatis-plus-spring-boot4-starter** — MyBatis-Plus
- **mysql-connector-j** — MySQL 驱动
- **lombok** — getter/setter 生成
- **hutool-all** — Java 工具类库（含 BCrypt 密码加密）
- **jjwt (0.12.7)** — JWT 令牌创建与解析
- **logback-spring.xml** — 日志按天滚动，保留 30 天

### 数据库

8 张表（`schema.sql`）：

| 表 | 说明 |
|----|------|
| user_account | 用户/管理员账号 |
| car | 车辆信息 |
| charging_order | 充电订单 |
| charging_session | 充电过程 |
| bill | 账单 |
| charging_pile | 充电桩 |
| billing_rate_period | 分时电价 |
| fault_record | 故障记录 |

## 项目结构

```
src/main/java/bupt/evchargebackend/
├── EvchargebackendApplication.java   # 启动入口
├── common/
│   ├── exception/                    # BusinessException、GlobalExceptionHandler、ErrorCode
│   ├── jwt/                          # JwtUtil、JwtInterceptor（未启用）
│   └── response/                     # Result、PageResult
├── config/
│   ├── CorsConfig.java               # CORS 跨域
│   ├── MyBatisPlusConfig.java        # @MapperScan
│   ├── TimeMetaObjectHandler.java    # 时间自动填充
│   └── WebConfig.java                # 拦截器注册（注释）
├── controller/
│   ├── health/DbHealthController.java
│   └── hello/HelloController.java
├── entity/
│   ├── user/   UserAccount.java, Car.java, UserRole.java
│   ├── charging/ ChargingOrder.java, ChargingSession.java, ...
│   ├── pile/   ChargingPile.java, PileType.java, ...
│   ├── bill/   Bill.java, PaymentStatus.java
│   ├── pricing/ BillingRatePeriod.java, PeriodName.java
│   └── fault/  FaultRecord.java, FaultStatus.java
├── mapper/                           # 8 个 Mapper 接口
├── service/
│   └── hello/                        # 示例服务
src/main/resources/
├── application.yml                   # 公共配置
├── application-dev.yml               # 本地开发
├── application-prod.yml              # 服务器部署
├── logback-spring.xml                # 日志配置
└── schema.sql                        # 建表脚本
```

## 快速开始

1. **确保 JDK 21 已安装**
   ```bash
   java -version
   ```

2. **启动 MySQL（本地或 Docker）**
   ```bash
   # Homebrew
   brew install mysql && brew services start mysql
   mysql -uroot -e "CREATE DATABASE evcharge DEFAULT CHARACTER SET utf8mb4;"
   mysql -uroot evcharge < schema.sql

   # 或 Docker
   docker run -d -e MYSQL_DATABASE=evcharge -e MYSQL_ROOT_PASSWORD=root -p 3306:3306 mysql:8.0
   ```

3. **IDEA 打开项目**，Maven 同步依赖，选择 `dev` profile 运行

4. **验证**
   ```bash
   curl http://localhost:8080/hello
   curl http://localhost:8080/db/health
   ```

## 多环境配置

| 文件 | 用途 | 启动方式 |
|------|------|---------|
| `application.yml` | 公共配置 | 自动加载 |
| `application-dev.yml` | 本地开发（打印 SQL） | `--spring.profiles.active=dev` |
| `application-prod.yml` | 服务器部署（Docker） | `--spring.profiles.active=prod` |

## 开发规范

### Git 提交

采用 Angular Commit 规范。

```
<type>(<scope>): <subject>
```

| type | 说明 |
|------|------|
| `feat` | 新功能 |
| `fix` | 修复 Bug |
| `build` | 构建/依赖变更 |
| `ci` | CI 配置变更 |
| `docs` | 文档 |
| `perf` | 性能优化 |
| `refactor` | 重构 |
| `test` | 测试 |

subject 规则：祈使语气、首字母小写、不加句号。

示例：
```
feat(device): add CRUD endpoints for device management
fix(pricing): correct peak hour rate calculation precision
```

### 注释

```java
/**
 * 统一 API 响应体包装。
 *
 * @author XXX
 * @since 2026-06-12
 */
public class Result<T> { ... }
```

### 配置约定

尽量不修改以下文件，如需修改请讨论后确认：

- `application.yml` / `application-dev.yml` / `application-prod.yml`
- `.github/workflows/deploy.yml`
- `Dockerfile` / `docker-compose.yml` / `docker-compose.prod.yml`
- `.gitignore`
- `pom.xml`（增删依赖时需确认）

### 推送

每完成一个小功能直接推送到 `main` 分支，保持频繁提交。

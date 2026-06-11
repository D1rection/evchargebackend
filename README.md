# 波普特大学智能充电桩调度计费系统后端

软件工程课程大作业 — 智能充电桩调度计费系统的后端服务。

## 技术栈

| 技术 | 版本 |
|------|------|
| Java | 21 |
| Spring Boot | 4.1.0 |
| 构建工具 | Maven |
| 数据库 | MySQL（待接入）|
| ORM | MyBatis-Plus（待接入）|

### 已集成依赖

- **spring-boot-starter-webmvc** — Web MVC + Jackson 3 序列化
- **spring-boot-starter-validation** — 参数校验（`@Valid`、`@NotNull`）
- **lombok** — 编译期生成 getter/setter/构造器
- **hutool-all** — Java 工具类库
- **jjwt (0.12.7)** — JWT 令牌创建与解析
- **logback-spring.xml** — 日志按天滚动，保留 30 天

## 项目结构

```
src/main/java/bupt/evchargebackend/
├── EvchargebackendApplication.java   # 启动入口
├── common/
│   ├── exception/
│   │   ├── BusinessException.java    # 业务异常，携带错误码
│   │   └── GlobalExceptionHandler.java # 全局异常处理（6 种异常类型）
│   └── response/
│       ├── Result.java               # 统一响应体 {code, msg, data}
│       └── PageResult.java           # 分页包装 {list, total, pageNum, pageSize}
├── config/
│   └── CorsConfig.java               # CORS 跨域配置
├── controller/
│   └── HelloController.java          # 健康检查
└── service/
    └── hello/
        ├── HelloService.java         # 接口
        └── impl/
            └── HelloServiceImpl.java # 实现
```

## 快速开始

1. **确保 JDK 21 已安装**
   ```bash
   java -version
   # 输出应为 openjdk version "21.0.x"
   ```

2. **用 IntelliJ IDEA 打开项目**
   - 项目根目录选择 `/evchargebackend`
   - IDEA 自动识别 Maven 项目并同步依赖

3. **运行应用**
   - 打开 `EvchargebackendApplication.java`
   - 点击行号旁的绿色三角运行

4. **验证**
   ```bash
   curl http://localhost:8080/hello
   ```
   返回 `{"code":200,"msg":"success","data":["Hello World!","你好，世界！"]}`

## 开发规范

### Git 提交

采用 Angular Commit 规范。

每条提交由 header、body、footer 三部分组成：

```
<type>(<scope>): <subject>
<BLANK LINE>
<body>
<BLANK LINE>
<footer>
```

**type（必填）：**

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

**scope（选填）：** 影响范围，如模块名称（`admin`、`scheduling`、`billing`）

**subject 规则：**
- 祈使语气，现在时（`add` 不是 `added` 或 `adds`）
- 首字母不大写
- 结尾不加句号

示例：

```
feat(device): add CRUD endpoints for device management
fix(pricing): correct peak hour rate calculation precision
docs: update API documentation
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

### 推送

每完成一个小功能直接推送到 `main` 分支，保持频繁提交。

package bupt.evchargebackend.controller.health;

import bupt.evchargebackend.common.response.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据库连接健康检查。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
@RestController
public class DbHealthController {

    private final JdbcTemplate jdbcTemplate;

    public DbHealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/db/health")
    public Result<Map<String, Object>> health() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return Result.success(Map.of("database", "MySQL", "connected", result == 1));
    }
}

package bupt.evchargebackend.service.admin.impl;

import bupt.evchargebackend.common.response.PageResult;
import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.service.admin.AdminMonitorService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 管理员监控统计服务实现。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@Service
public class AdminMonitorServiceImpl implements AdminMonitorService {

    private final JdbcTemplate jdbcTemplate;

    public AdminMonitorServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Result<PageResult<Map<String, Object>>> listPileStatus(Integer pageNum, Integer pageSize) {
        String baseSql = """
                SELECT
                    cp.pile_id AS pileId,
                    cp.pile_type AS pileType,
                    cp.power_state AS powerState,
                    cp.working_state AS workingState,
                    cs.car_id AS currentCarId,
                    c.car_no AS currentCarNo,
                    CASE WHEN cs.start_time IS NOT NULL
                         THEN TIMESTAMPDIFF(MINUTE, cs.start_time, NOW())
                         ELSE 0 END AS currentChargeDuration,
                    cp.total_charge_count AS totalChargeCount,
                    CONCAT(FLOOR(cp.total_charge_minutes / 60), ':', LPAD(cp.total_charge_minutes % 60, 2, '0'), ':00') AS totalChargeTime,
                    cp.total_charge_kwh AS totalCapacity
                FROM charging_pile cp
                LEFT JOIN charging_session cs
                    ON cp.current_session_id = cs.session_id AND cs.session_status = 'CHARGING'
                LEFT JOIN car c ON cs.car_id = c.car_id
                ORDER BY cp.pile_id
                """;

        if (pageNum == null || pageSize == null) {
            List<Map<String, Object>> all = jdbcTemplate.queryForList(baseSql);
            return Result.success(PageResult.of(all, all.size(), 1, all.size()));
        }

        int offset = (pageNum - 1) * pageSize;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM charging_pile", Long.class);

        String pageSql = baseSql + " LIMIT " + pageSize + " OFFSET " + offset;
        List<Map<String, Object>> list = jdbcTemplate.queryForList(pageSql);

        for (Map<String, Object> row : list) {
            convertDecimal(row, "totalCapacity");
        }

        return Result.success(PageResult.of(list, total != null ? total : 0, pageNum, pageSize));
    }

    @Override
    public Result<Map<String, Object>> getDashboard() {
        Long todayChargeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM charging_session WHERE DATE(start_time) = CURDATE()", Long.class);
        BigDecimal todayRevenue = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total_fee), 0) FROM bill WHERE DATE(created_at) = CURDATE()", BigDecimal.class);
        BigDecimal onlineRate = jdbcTemplate.queryForObject("""
                SELECT ROUND(
                    COALESCE(SUM(CASE WHEN power_state = 'ON' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 0)
                , 1) FROM charging_pile""", BigDecimal.class);
        Long faultCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fault_record WHERE fault_status = 'ACTIVE'", Long.class);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayChargeCount", todayChargeCount != null ? todayChargeCount.intValue() : 0);
        result.put("todayRevenue", todayRevenue != null ? todayRevenue.doubleValue() : 0.0);
        result.put("onlineRate", onlineRate != null ? onlineRate.doubleValue() : 0.0);
        result.put("faultCount", faultCount != null ? faultCount.intValue() : 0);
        return Result.success(result);
    }

    private void convertDecimal(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val instanceof BigDecimal bd) {
            row.put(key, bd.doubleValue());
        }
    }
}

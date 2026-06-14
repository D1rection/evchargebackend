package bupt.evchargebackend.service.admin.impl;

import bupt.evchargebackend.common.response.PageResult;
import bupt.evchargebackend.service.admin.AdminMonitorService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    public PageResult<Map<String, Object>> listPileStatus(Integer pageNum, Integer pageSize) {
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

        // 如果不传分页参数，返回全部
        if (pageNum == null || pageSize == null) {
            List<Map<String, Object>> all = jdbcTemplate.queryForList(baseSql);
            return PageResult.of(all, all.size(), 1, all.size());
        }

        // 分页查询
        int offset = (pageNum - 1) * pageSize;
        String countSql = "SELECT COUNT(*) FROM charging_pile";
        Long total = jdbcTemplate.queryForObject(countSql, Long.class);

        String pageSql = baseSql + " LIMIT " + pageSize + " OFFSET " + offset;
        List<Map<String, Object>> list = jdbcTemplate.queryForList(pageSql);

        // 转换 BigDecimal 为适当的类型
        for (Map<String, Object> row : list) {
            convertDecimal(row, "totalCapacity");
        }

        return PageResult.of(list, total != null ? total : 0, pageNum, pageSize);
    }

    @Override
    public Map<String, Object> getDashboard() {
        // 今日充电次数
        String countSql = "SELECT COUNT(*) FROM charging_session WHERE DATE(start_time) = CURDATE()";
        Long todayChargeCount = jdbcTemplate.queryForObject(countSql, Long.class);

        // 今日收入
        String revenueSql = "SELECT COALESCE(SUM(total_fee), 0) FROM bill WHERE DATE(created_at) = CURDATE()";
        BigDecimal todayRevenue = jdbcTemplate.queryForObject(revenueSql, BigDecimal.class);

        // 设备在线率
        String onlineSql = """
                SELECT ROUND(
                    COALESCE(SUM(CASE WHEN power_state = 'ON' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 0)
                , 1) FROM charging_pile""";
        BigDecimal onlineRate = jdbcTemplate.queryForObject(onlineSql, BigDecimal.class);

        // 当前故障数
        String faultSql = "SELECT COUNT(*) FROM fault_record WHERE fault_status = 'ACTIVE'";
        Long faultCount = jdbcTemplate.queryForObject(faultSql, Long.class);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayChargeCount", todayChargeCount != null ? todayChargeCount.intValue() : 0);
        result.put("todayRevenue", todayRevenue != null ? todayRevenue.doubleValue() : 0.0);
        result.put("onlineRate", onlineRate != null ? onlineRate.doubleValue() : 0.0);
        result.put("faultCount", faultCount != null ? faultCount.intValue() : 0);
        return result;
    }

    private void convertDecimal(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val instanceof BigDecimal bd) {
            row.put(key, bd.doubleValue());
        }
    }
}

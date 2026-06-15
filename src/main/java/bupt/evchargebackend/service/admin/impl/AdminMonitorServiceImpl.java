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
 * <p>
 * 使用 {@link JdbcTemplate} 直接查询数据库，聚合充电桩状态和运营概览数据。
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

    /**
     * 获取充电桩状态列表，支持分页。
     * <p>
     * 通过 LEFT JOIN 关联当前充电会话和车辆信息，实时展示每台桩的运行状态。
     * 不传分页参数时返回全部数据。
     *
     * @param pageNum  页码，{@code null} 表示不分页
     * @param pageSize 每页条数，{@code null} 表示不分页
     * @return 分页包装的充电桩状态列表，含 pileId、pileType、powerState、workingState、currentCarNo 等
     */
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

        if (pageNum == null || pageSize == null) {
            List<Map<String, Object>> all = jdbcTemplate.queryForList(baseSql);
            return PageResult.of(all, all.size(), 1, all.size());
        }

        int offset = (pageNum - 1) * pageSize;
        String countSql = "SELECT COUNT(*) FROM charging_pile";
        Long total = jdbcTemplate.queryForObject(countSql, Long.class);

        String pageSql = baseSql + " LIMIT " + pageSize + " OFFSET " + offset;
        List<Map<String, Object>> list = jdbcTemplate.queryForList(pageSql);

        for (Map<String, Object> row : list) {
            convertDecimal(row, "totalCapacity");
        }

        return PageResult.of(list, total != null ? total : 0, pageNum, pageSize);
    }

    /**
     * 获取仪表盘概览数据。
     * <p>
     * 聚合今日充电次数、今日收入、设备在线率和当前故障数四项核心指标。
     *
     * @return 仪表盘数据，含 {@code todayChargeCount}、{@code todayRevenue}、{@code onlineRate}、{@code faultCount}
     */
    @Override
    public Map<String, Object> getDashboard() {
        String countSql = "SELECT COUNT(*) FROM charging_session WHERE DATE(start_time) = CURDATE()";
        Long todayChargeCount = jdbcTemplate.queryForObject(countSql, Long.class);

        String revenueSql = "SELECT COALESCE(SUM(total_fee), 0) FROM bill WHERE DATE(created_at) = CURDATE()";
        BigDecimal todayRevenue = jdbcTemplate.queryForObject(revenueSql, BigDecimal.class);

        String onlineSql = """
                SELECT ROUND(
                    COALESCE(SUM(CASE WHEN power_state = 'ON' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 0)
                , 1) FROM charging_pile""";
        BigDecimal onlineRate = jdbcTemplate.queryForObject(onlineSql, BigDecimal.class);

        String faultSql = "SELECT COUNT(*) FROM fault_record WHERE fault_status = 'ACTIVE'";
        Long faultCount = jdbcTemplate.queryForObject(faultSql, Long.class);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayChargeCount", todayChargeCount != null ? todayChargeCount.intValue() : 0);
        result.put("todayRevenue", todayRevenue != null ? todayRevenue.doubleValue() : 0.0);
        result.put("onlineRate", onlineRate != null ? onlineRate.doubleValue() : 0.0);
        result.put("faultCount", faultCount != null ? faultCount.intValue() : 0);
        return result;
    }

    /**
     * 将行数据中的 {@link BigDecimal} 值转换为 {@link Double}，方便 Jackson 序列化。
     *
     * @param row 行数据 Map
     * @param key 需要转换的字段名
     */
    private void convertDecimal(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val instanceof BigDecimal bd) {
            row.put(key, bd.doubleValue());
        }
    }
}

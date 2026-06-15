package bupt.evchargebackend.service.admin.impl;

import bupt.evchargebackend.common.exception.BusinessException;
import bupt.evchargebackend.service.admin.AdminReportService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * 管理员运营报表服务实现。
 * <p>
 * 使用 {@link JdbcTemplate} 从账单、充电会话、故障表中聚合运营数据，
 * 支持按日/周/月/自定义时间范围统计。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@Service
public class AdminReportServiceImpl implements AdminReportService {

    private final JdbcTemplate jdbcTemplate;

    public AdminReportServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 生成运营统计数据。
     * <p>
     * 从 {@code bill} 表聚合收入与充电量，从 {@code charging_session} 统计充电次数，
     * 从 {@code fault_record} 计算故障率。
     *
     * @param targetType 统计目标类型（{@code all}=全部桩，{@code single}=单桩）
     * @param pileId     单桩统计时必填
     * @param timeRange  时间范围类型（{@code day/week/month/custom}）
     * @param startDate  自定义起始日期（{@code yyyy-MM-dd}）
     * @param endDate    自定义结束日期（{@code yyyy-MM-dd}）
     * @return 运营统计 Map，含 totalChargeCount、totalChargeAmount、totalRevenue、
     *         totalChargeFee、totalServiceFee、avgChargeDuration、faultRate
     */
    @Override
    public Map<String, Object> generateReport(String targetType, String pileId,
                                              String timeRange, String startDate, String endDate) {
        Map<String, LocalDateTime> range = calculateTimeRange(timeRange, startDate, endDate);
        LocalDateTime start = range.get("start");
        LocalDateTime end = range.get("end");

        String pileCondition = "";
        List<Object> params = new ArrayList<>();
        params.add(start);
        params.add(end);

        if ("single".equals(targetType)) {
            if (pileId == null || pileId.isEmpty()) {
                throw new BusinessException(400, "单桩统计时 pileId 为必填");
            }
            pileCondition = " AND pile_id = ?";
            params.add(pileId);
        }

        String countSql = "SELECT COUNT(*) FROM charging_session cs " +
                "WHERE cs.start_time >= ? AND cs.start_time <= ?" +
                (params.size() > 2 ? " AND cs.pile_id = ?" : "");
        Object[] countParams = params.size() > 2
                ? new Object[]{start, end, params.get(2)}
                : new Object[]{start, end};
        Long totalChargeCount = jdbcTemplate.queryForObject(countSql, Long.class, countParams);

        String billSql = """
                SELECT
                    COALESCE(SUM(b.charged_kwh), 0) AS totalChargeAmount,
                    COALESCE(SUM(b.total_fee), 0) AS totalRevenue,
                    COALESCE(SUM(b.electricity_fee), 0) AS totalChargeFee,
                    COALESCE(SUM(b.service_fee), 0) AS totalServiceFee,
                    COALESCE(AVG(b.charge_minutes), 0) AS avgChargeDuration
                FROM bill b
                WHERE b.start_time >= ? AND b.start_time <= ?
                """ + pileCondition;
        Object[] billParams = params.toArray();
        Map<String, Object> billData = jdbcTemplate.queryForMap(billSql, billParams);

        String pileSubQuery = "SELECT COUNT(*) FROM charging_pile";
        if ("single".equals(targetType) && pileId != null) {
            pileSubQuery += " WHERE pile_id = '" + pileId + "'";
        }
        String faultSql = "SELECT " +
                "CASE WHEN COUNT(*) > 0 " +
                "THEN ROUND(COUNT(*) * 100.0 / (" + pileSubQuery + "), 1) " +
                "ELSE 0 END AS faultRate " +
                "FROM fault_record f " +
                "WHERE f.fault_time >= ? AND f.fault_time <= ?" +
                pileCondition;
        Object[] faultParams = params.toArray();
        Map<String, Object> faultData = jdbcTemplate.queryForMap(faultSql, faultParams);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalChargeCount", totalChargeCount != null ? totalChargeCount.intValue() : 0);
        result.put("totalChargeAmount", toDouble(billData.get("totalChargeAmount")));
        result.put("totalRevenue", toDouble(billData.get("totalRevenue")));
        result.put("totalChargeFee", toDouble(billData.get("totalChargeFee")));
        result.put("totalServiceFee", toDouble(billData.get("totalServiceFee")));
        result.put("avgChargeDuration", toInt(billData.get("avgChargeDuration")));
        result.put("faultRate", toDouble(faultData.get("faultRate")));
        return result;
    }

    /**
     * 导出报表为 CSV 文件。
     *
     * @param targetType 统计目标类型
     * @param pileId     单桩统计时必填
     * @param timeRange  时间范围类型
     * @param startDate  自定义起始日期
     * @param endDate    自定义结束日期
     * @return CSV 文件字节数组（UTF-8 编码，BOM 头）
     */
    @Override
    public byte[] exportReport(String targetType, String pileId,
                               String timeRange, String startDate, String endDate) {
        Map<String, Object> data = generateReport(targetType, pileId, timeRange, startDate, endDate);

        StringBuilder csv = new StringBuilder();
        csv.append("指标,值\n");
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            csv.append(entry.getKey()).append(",").append(entry.getValue()).append("\n");
        }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 根据时间范围类型计算查询的起止时间。
     *
     * @param timeRange 时间范围类型（{@code day/week/month/custom}）
     * @param startDate 自定义起始日期
     * @param endDate   自定义结束日期
     * @return {@code {start: LocalDateTime, end: LocalDateTime}}
     * @throws BusinessException 自定义模式缺参数或参数无效时抛出（400）
     */
    private Map<String, LocalDateTime> calculateTimeRange(String timeRange, String startDate, String endDate) {
        LocalDate today = LocalDate.now();
        LocalDateTime start;
        LocalDateTime end = LocalDateTime.now();

        switch (timeRange) {
            case "day":
                start = today.atStartOfDay();
                break;
            case "week":
                start = today.with(DayOfWeek.MONDAY).atStartOfDay();
                break;
            case "month":
                start = today.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
                break;
            case "custom":
                if (startDate == null || endDate == null) {
                    throw new BusinessException(400, "自定义时间范围时 startDate 和 endDate 为必填");
                }
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                start = LocalDate.parse(startDate, fmt).atStartOfDay();
                end = LocalDate.parse(endDate, fmt).atTime(23, 59, 59);
                break;
            default:
                throw new BusinessException(400, "无效的时间范围: " + timeRange);
        }

        Map<String, LocalDateTime> result = new HashMap<>();
        result.put("start", start);
        result.put("end", end);
        return result;
    }

    /**
     * 将数据库查询值安全转换为 {@code double}，{@code null} 时返回 0.0。
     */
    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof BigDecimal bd) return bd.doubleValue();
        if (val instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    /**
     * 将数据库查询值安全转换为 {@code int}，{@code null} 时返回 0。
     */
    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof BigDecimal bd) return bd.intValue();
        if (val instanceof Number n) return n.intValue();
        return 0;
    }
}

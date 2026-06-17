package bupt.evchargebackend.service.admin.impl;

import bupt.evchargebackend.common.exception.BusinessException;
import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.service.admin.AdminReportService;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
public class AdminReportServiceImpl implements AdminReportService {

    private final JdbcTemplate jdbcTemplate;

    public AdminReportServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Result<Map<String, Object>> generateReport(String targetType, String pileId,
                                                      String timeRange, String startDate, String endDate) {
        Map<String, LocalDateTime> range = calculateTimeRange(timeRange, startDate, endDate);
        LocalDateTime start = range.get("start");
        LocalDateTime end = range.get("end");

        List<Object> params = new ArrayList<>();
        params.add(start);
        params.add(end);

        String pileCondition = "";
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
        Map<String, Object> billData = jdbcTemplate.queryForMap(billSql, params.toArray());

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
        Map<String, Object> faultData = jdbcTemplate.queryForMap(faultSql, params.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalChargeCount", totalChargeCount != null ? totalChargeCount.intValue() : 0);
        result.put("totalChargeAmount", toDouble(billData.get("totalChargeAmount")));
        result.put("totalRevenue", toDouble(billData.get("totalRevenue")));
        result.put("totalChargeFee", toDouble(billData.get("totalChargeFee")));
        result.put("totalServiceFee", toDouble(billData.get("totalServiceFee")));
        result.put("avgChargeDuration", toInt(billData.get("avgChargeDuration")));
        result.put("faultRate", toDouble(faultData.get("faultRate")));
        return Result.success(result);
    }

    @Override
    public byte[] exportReport(String targetType, String pileId,
                               String timeRange, String startDate, String endDate) {
        Map<String, Object> data = generateReport(targetType, pileId, timeRange, startDate, endDate).getData();

        // 字段中文映射
        Map<String, String> headerMap = new LinkedHashMap<>();
        headerMap.put("totalChargeCount", "充电次数");
        headerMap.put("totalChargeAmount", "充电电量(kWh)");
        headerMap.put("totalRevenue", "总营收(元)");
        headerMap.put("totalChargeFee", "电费(元)");
        headerMap.put("totalServiceFee", "服务费(元)");
        headerMap.put("avgChargeDuration", "平均充电时长(分钟)");
        headerMap.put("faultRate", "故障率(%)");

        try (ExcelWriter writer = ExcelUtil.getWriter(true);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 写表头
            writer.writeCellValue(0, 0, "指标");
            writer.writeCellValue(1, 0, "值");

            // 写数据行
            int row = 1;
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String label = headerMap.getOrDefault(entry.getKey(), entry.getKey());
                writer.writeCellValue(0, row, label);
                writer.writeCellValue(1, row, entry.getValue());
                row++;
            }

            // 设置列宽（单位：字符数）
            writer.setColumnWidth(0, 20);
            writer.setColumnWidth(1, 15);

            writer.flush(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("导出 Excel 失败", e);
        }
    }

    private Map<String, LocalDateTime> calculateTimeRange(String timeRange, String startDate, String endDate) {
        LocalDate today = LocalDate.now();
        LocalDateTime start;
        LocalDateTime end = LocalDateTime.now();

        switch (timeRange) {
            case "day" -> start = today.atStartOfDay();
            case "week" -> start = today.with(DayOfWeek.MONDAY).atStartOfDay();
            case "month" -> start = today.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
            case "custom" -> {
                if (startDate == null || endDate == null) {
                    throw new BusinessException(400, "自定义时间范围时 startDate 和 endDate 为必填");
                }
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                start = LocalDate.parse(startDate, fmt).atStartOfDay();
                end = LocalDate.parse(endDate, fmt).atTime(23, 59, 59);
            }
            default -> throw new BusinessException(400, "无效的时间范围: " + timeRange);
        }

        Map<String, LocalDateTime> result = new HashMap<>();
        result.put("start", start);
        result.put("end", end);
        return result;
    }

    private double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof BigDecimal bd) return bd.doubleValue();
        if (val instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof BigDecimal bd) return bd.intValue();
        if (val instanceof Number n) return n.intValue();
        return 0;
    }
}

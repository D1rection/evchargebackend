package bupt.evchargebackend.service.admin;

import bupt.evchargebackend.common.response.Result;

import java.util.Map;

public interface AdminReportService {
    Result<Map<String, Object>> generateReport(String targetType, String pileId,
                                               String timeRange, String startDate, String endDate);
    byte[] exportReport(String targetType, String pileId,
                        String timeRange, String startDate, String endDate);
}

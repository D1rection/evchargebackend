package bupt.evchargebackend.service.admin;

import java.util.Map;

public interface AdminReportService {
    Map<String, Object> generateReport(String targetType, String pileId,
                                       String timeRange, String startDate, String endDate);
    byte[] exportReport(String targetType, String pileId,
                        String timeRange, String startDate, String endDate);
}

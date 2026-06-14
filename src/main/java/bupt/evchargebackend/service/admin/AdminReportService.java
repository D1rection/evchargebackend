package bupt.evchargebackend.service.admin;

import java.util.Map;

/**
 * 管理员运营报表服务。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
public interface AdminReportService {

    /** 生成运营统计数据 */
    Map<String, Object> generateReport(String targetType, String pileId,
                                       String timeRange, String startDate, String endDate);

    /** 导出报表文件，返回文件字节数组 */
    byte[] exportReport(String targetType, String pileId,
                        String timeRange, String startDate, String endDate);
}

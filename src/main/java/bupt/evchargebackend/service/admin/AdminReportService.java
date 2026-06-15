package bupt.evchargebackend.service.admin;

import bupt.evchargebackend.common.response.Result;

import java.util.Map;

/**
 * 管理员运营报表服务。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
public interface AdminReportService {

    /**
     * 生成运营统计数据。
     *
     * @param targetType 统计目标类型
     * @param pileId     单桩统计时必填
     * @param timeRange  时间范围
     * @param startDate  自定义起始日期
     * @param endDate    自定义结束日期
     * @return 运营统计数据；参数无效时 {@code code=400}
     */
    Result<Map<String, Object>> generateReport(String targetType, String pileId,
                                               String timeRange, String startDate, String endDate);

    /**
     * 导出报表为 CSV 文件。
     */
    byte[] exportReport(String targetType, String pileId,
                        String timeRange, String startDate, String endDate);
}

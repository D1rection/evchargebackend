package bupt.evchargebackend.service.admin;

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
     * @param targetType 统计目标类型（{@code all}=全部桩，{@code single}=单桩）
     * @param pileId     单桩统计时必填，桩 ID
     * @param timeRange  时间范围（{@code day}=今日，{@code week}=本周，{@code month}=本月，{@code custom}=自定义）
     * @param startDate  自定义起始日期（{@code yyyy-MM-dd}）
     * @param endDate    自定义结束日期（{@code yyyy-MM-dd}）
     * @return 运营统计数据，含总充电次数、总收入、故障率等
     */
    Map<String, Object> generateReport(String targetType, String pileId,
                                       String timeRange, String startDate, String endDate);

    /**
     * 导出报表为 CSV 文件。
     *
     * @param targetType 统计目标类型（{@code all}=全部桩，{@code single}=单桩）
     * @param pileId     单桩统计时必填，桩 ID
     * @param timeRange  时间范围（{@code day}=今日，{@code week}=本周，{@code month}=本月，{@code custom}=自定义）
     * @param startDate  自定义起始日期
     * @param endDate    自定义结束日期
     * @return CSV 文件字节数组（UTF-8 编码）
     */
    byte[] exportReport(String targetType, String pileId,
                        String timeRange, String startDate, String endDate);
}

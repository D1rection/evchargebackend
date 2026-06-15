package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.service.admin.AdminReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员运营报表 Controller。
 * <p>
 * 提供运营数据统计和报表导出（CSV）功能。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@RestController
@RequestMapping("/admin")
public class AdminReportController {

    private final AdminReportService adminReportService;

    public AdminReportController(AdminReportService adminReportService) {
        this.adminReportService = adminReportService;
    }

    /**
     * 生成运营统计数据。
     *
     * @param targetType 统计目标类型（{@code all}=全部桩，{@code single}=单桩）
     * @param pileId     单桩统计时必填，桩 ID
     * @param timeRange  时间范围（{@code day}=今日，{@code week}=本周，{@code month}=本月，{@code custom}=自定义）
     * @param startDate  自定义起始日期（{@code yyyy-MM-dd}），仅在 {@code timeRange=custom} 时使用
     * @param endDate    自定义结束日期（{@code yyyy-MM-dd}），仅在 {@code timeRange=custom} 时使用
     * @return 运营统计数据，含总充电次数、总收入、故障率等；参数无效时 {@code code=400}
     */
    @GetMapping("/reports")
    public Result<Map<String, Object>> generateReport(
            @RequestParam String targetType,
            @RequestParam(required = false) String pileId,
            @RequestParam String timeRange,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.of(() -> adminReportService.generateReport(
                targetType, pileId, timeRange, startDate, endDate));
    }

    /**
     * 导出报表为 CSV 文件。
     * <p>
     * 注意：导出接口直接返回 {@link ResponseEntity}，不使用 {@link Result} 包装。
     *
     * @param targetType 统计目标类型（{@code all}=全部桩，{@code single}=单桩）
     * @param pileId     单桩统计时必填，桩 ID
     * @param timeRange  时间范围（{@code day}=今日，{@code week}=本周，{@code month}=本月，{@code custom}=自定义）
     * @param startDate  自定义起始日期，仅在 {@code timeRange=custom} 时使用
     * @param endDate    自定义结束日期，仅在 {@code timeRange=custom} 时使用
     * @return CSV 文件字节流，Content-Disposition 为 {@code attachment; filename=report.csv}
     */
    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam String targetType,
            @RequestParam(required = false) String pileId,
            @RequestParam String timeRange,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        byte[] data = adminReportService.exportReport(
                targetType, pileId, timeRange, startDate, endDate);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=report.csv")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }
}

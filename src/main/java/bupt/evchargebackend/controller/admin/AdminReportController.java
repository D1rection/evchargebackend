package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.service.admin.AdminReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminReportController {

    private final AdminReportService adminReportService;

    public AdminReportController(AdminReportService adminReportService) {
        this.adminReportService = adminReportService;
    }

    /**
     * 生成统计报表：支持按日/周/月/自定义时间范围，可按全站或单桩统计。
     *
     * @param targetType 统计目标（all / single）
     * @param pileId     单桩统计时的充电桩ID（targetType=single 时必填）
     * @param timeRange  时间范围（day / week / month / custom）
     * @param startDate  自定义起始日期（timeRange=custom 时必填）
     * @param endDate    自定义结束日期（timeRange=custom 时必填）
     * @return 报表数据
     */
    @GetMapping("/reports")
    public Result<Map<String, Object>> generateReport(
            @RequestParam String targetType,
            @RequestParam(required = false) String pileId,
            @RequestParam String timeRange,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return adminReportService.generateReport(targetType, pileId, timeRange, startDate, endDate);
    }

    /**
     * 导出报表为 CSV 文件下载。
     *
     * @param targetType 统计目标（all / single）
     * @param pileId     单桩统计时的充电桩ID
     * @param timeRange  时间范围（day / week / month / custom）
     * @param startDate  自定义起始日期
     * @param endDate    自定义结束日期
     * @return CSV 文件字节流
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

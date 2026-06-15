package bupt.evchargebackend.controller.admin;

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

    @GetMapping("/reports")
    public Map<String, Object> generateReport(
            @RequestParam String targetType,
            @RequestParam(required = false) String pileId,
            @RequestParam String timeRange,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return adminReportService.generateReport(targetType, pileId, timeRange, startDate, endDate);
    }

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

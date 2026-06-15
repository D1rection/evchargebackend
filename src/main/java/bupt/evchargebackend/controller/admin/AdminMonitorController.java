package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.response.PageResult;
import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.service.admin.AdminMonitorService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员监控统计 Controller。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@RestController
@RequestMapping("/admin")
public class AdminMonitorController {

    private final AdminMonitorService adminMonitorService;

    public AdminMonitorController(AdminMonitorService adminMonitorService) {
        this.adminMonitorService = adminMonitorService;
    }

    @GetMapping("/piles")
    public Result<PageResult<Map<String, Object>>> listPiles(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return adminMonitorService.listPileStatus(pageNum, pageSize);
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return adminMonitorService.getDashboard();
    }
}

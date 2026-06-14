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

    /** 获取充电桩状态列表 */
    @GetMapping("/piles")
    public Result<PageResult<Map<String, Object>>> listPiles(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return Result.success(adminMonitorService.listPileStatus(pageNum, pageSize));
    }

    /** 获取仪表盘数据 */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.success(adminMonitorService.getDashboard());
    }
}

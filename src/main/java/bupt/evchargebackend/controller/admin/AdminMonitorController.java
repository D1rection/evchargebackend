package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.response.PageResult;
import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.service.admin.AdminMonitorService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员监控统计 Controller。
 * <p>
 * 提供充电桩实时状态监控和仪表盘概览数据。
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

    /**
     * 获取充电桩状态列表，支持分页。
     * <p>
     * 不传分页参数时返回全部数据。
     *
     * @param pageNum  页码（从 1 开始），可选
     * @param pageSize 每页条数，可选
     * @return 分页包装的充电桩状态列表
     */
    @GetMapping("/piles")
    public Result<PageResult<Map<String, Object>>> listPiles(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return Result.success(adminMonitorService.listPileStatus(pageNum, pageSize));
    }

    /**
     * 获取仪表盘概览数据。
     * <p>
     * 包含今日充电次数、今日收入、设备在线率、当前故障数等关键指标。
     *
     * @return 仪表盘数据 Map
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.success(adminMonitorService.getDashboard());
    }
}

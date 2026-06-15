package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.response.PageResult;
import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.service.admin.AdminMonitorService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminMonitorController {

    private final AdminMonitorService adminMonitorService;

    public AdminMonitorController(AdminMonitorService adminMonitorService) {
        this.adminMonitorService = adminMonitorService;
    }

    /**
     * 分页查询充电桩实时状态：供电状态、工作状态、当前充电车辆等。
     *
     * @param pageNum  页码，可选（不分页时传 null）
     * @param pageSize 每页条数，可选（不分页时传 null）
     * @return 分页充电桩状态列表
     */
    @GetMapping("/piles")
    public Result<PageResult<Map<String, Object>>> listPiles(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return adminMonitorService.listPileStatus(pageNum, pageSize);
    }

    /**
     * 获取管理后台仪表盘数据：今日充电量、今日营收、在线率、活跃故障数。
     *
     * @return 仪表盘数据
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return adminMonitorService.getDashboard();
    }
}

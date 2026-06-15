package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.response.PageResult;
import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.FaultResolveRequest;
import bupt.evchargebackend.service.admin.AdminFaultService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员故障运维 Controller。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@RestController
@RequestMapping("/admin")
public class AdminFaultController {

    private final AdminFaultService adminFaultService;

    public AdminFaultController(AdminFaultService adminFaultService) {
        this.adminFaultService = adminFaultService;
    }

    /** 获取故障记录列表 */
    @GetMapping("/faults")
    public Result<PageResult<Map<String, Object>>> listFaults(
            @RequestParam int pageNum,
            @RequestParam int pageSize,
            @RequestParam(required = false) Integer status) {
        return Result.success(adminFaultService.listFaults(pageNum, pageSize, status));
    }

    /** 标记故障为已处置 */
    @PostMapping("/faults/{faultId}")
    public Result<Void> resolveFault(@PathVariable String faultId,
                                     @RequestBody FaultResolveRequest request) {
        adminFaultService.resolveFault(faultId, request.getResolveCode(), request.getRemark());
        return Result.success();
    }
}

package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.response.PageResult;
import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.FaultResolveRequest;
import bupt.evchargebackend.service.admin.AdminFaultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AdminFaultController.class);

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
        try {
            return Result.success(adminFaultService.listFaults(pageNum, pageSize, status));
        } catch (Exception e) {
            log.error("查询故障列表失败: pageNum={}, pageSize={}, status={}", pageNum, pageSize, status, e);
            throw e;
        }
    }

    /** 标记故障为已处置 */
    @PostMapping("/faults/{faultId}")
    public Result<Void> resolveFault(@PathVariable String faultId,
                                     @RequestBody FaultResolveRequest request) {
        try {
            adminFaultService.resolveFault(faultId, request.getResolveCode(), request.getRemark());
            return Result.success();
        } catch (Exception e) {
            log.error("处置故障失败: faultId={}, resolveCode={}", faultId, request.getResolveCode(), e);
            throw e;
        }
    }
}

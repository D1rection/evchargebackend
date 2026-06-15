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
 * <p>
 * 提供故障记录列表查询和故障处置功能。
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

    /**
     * 分页查询故障记录列表，支持按状态筛选。
     *
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @param status   故障状态筛选（1=待处置，2=已处置），{@code null} 表示全部
     * @return 分页包装的故障记录列表
     */
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

    /**
     * 标记故障为已处置。
     *
     * @param faultId 故障记录 ID
     * @param request 处置请求体，包含 {@code resolveCode}（处置码）和 {@code remark}（处置备注）
     * @return 空成功响应
     */
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

package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.response.PageResult;
import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.FaultResolveRequest;
import bupt.evchargebackend.service.admin.AdminFaultService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminFaultController {

    private final AdminFaultService adminFaultService;

    public AdminFaultController(AdminFaultService adminFaultService) {
        this.adminFaultService = adminFaultService;
    }

    /**
     * 分页查询故障记录，可按状态筛选。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param status   故障状态（1=活跃 / 2=已恢复），可选
     * @return 分页故障列表
     */
    @GetMapping("/faults")
    public Result<PageResult<Map<String, Object>>> listFaults(
            @RequestParam int pageNum,
            @RequestParam int pageSize,
            @RequestParam(required = false) Integer status) {
        return adminFaultService.listFaults(pageNum, pageSize, status);
    }

    /**
     * 处理故障：填写解决代码与备注，将故障标记为已恢复。
     *
     * @param faultId 故障ID
     * @param request 处理请求（resolveCode / remark）
     * @return 空结果
     */
    @PostMapping("/faults/{faultId}")
    public Result<Void> resolveFault(@PathVariable String faultId,
                                     @RequestBody FaultResolveRequest request) {
        return adminFaultService.resolveFault(faultId, request.getResolveCode(), request.getRemark());
    }
}

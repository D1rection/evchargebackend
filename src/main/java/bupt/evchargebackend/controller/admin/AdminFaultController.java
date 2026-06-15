package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.response.PageResult;
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

    @GetMapping("/faults")
    public PageResult<Map<String, Object>> listFaults(
            @RequestParam int pageNum,
            @RequestParam int pageSize,
            @RequestParam(required = false) Integer status) {
        return adminFaultService.listFaults(pageNum, pageSize, status);
    }

    @PostMapping("/faults/{faultId}")
    public void resolveFault(@PathVariable String faultId,
                             @RequestBody FaultResolveRequest request) {
        adminFaultService.resolveFault(faultId, request.getResolveCode(), request.getRemark());
    }
}

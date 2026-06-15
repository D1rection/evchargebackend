package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.dto.AdminAccountRequest;
import bupt.evchargebackend.service.admin.AdminAccountService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/account")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    public AdminAccountController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @PostMapping("/create")
    public Map<String, Object> create(@RequestBody AdminAccountRequest request) {
        return adminAccountService.register(request.getUserName(), request.getPassword());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody AdminAccountRequest request) {
        return adminAccountService.login(request.getUserName(), request.getPassword());
    }
}

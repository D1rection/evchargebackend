package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.PileRequest;
import bupt.evchargebackend.dto.StationConfigRequest;
import bupt.evchargebackend.service.admin.AdminStationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理员场站设备 Controller。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@RestController
@RequestMapping("/admin")
public class AdminStationController {

    private final AdminStationService adminStationService;

    public AdminStationController(AdminStationService adminStationService) {
        this.adminStationService = adminStationService;
    }

    @GetMapping("/station-config")
    public Result<Map<String, Object>> getStationConfig() {
        return adminStationService.getStationConfig();
    }

    @PostMapping("/station-config")
    public Result<Void> updateStationConfig(@RequestBody StationConfigRequest request) {
        return adminStationService.updateStationConfig(request);
    }

    @GetMapping("/devices")
    public Result<List<Map<String, Object>>> listDevices() {
        return adminStationService.listDevices();
    }

    @PostMapping("/devices")
    public Result<Map<String, Object>> addDevice(@RequestBody PileRequest request) {
        return adminStationService.addDevice(request);
    }

    @PostMapping("/devices/update/{pileId}")
    public Result<Void> updateDevice(@PathVariable String pileId,
                                     @RequestBody PileRequest request) {
        return adminStationService.updateDevice(pileId, request);
    }

    @PostMapping("/devices/delete/{pileId}")
    public Result<Void> deleteDevice(@PathVariable String pileId) {
        return adminStationService.deleteDevice(pileId);
    }
}

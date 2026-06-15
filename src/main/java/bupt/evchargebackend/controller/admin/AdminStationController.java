package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.PileRequest;
import bupt.evchargebackend.dto.StationConfigRequest;
import bupt.evchargebackend.service.admin.AdminStationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminStationController {

    private final AdminStationService adminStationService;

    public AdminStationController(AdminStationService adminStationService) {
        this.adminStationService = adminStationService;
    }

    @GetMapping("/station-config")
    public Result<Map<String, Object>> getStationConfig() {
        return Result.of(() -> adminStationService.getStationConfig());
    }

    @PostMapping("/station-config")
    public Result<Void> updateStationConfig(@RequestBody StationConfigRequest request) {
        return Result.ofVoid(() -> adminStationService.updateStationConfig(request));
    }

    @GetMapping("/devices")
    public Result<List<Map<String, Object>>> listDevices() {
        return Result.of(() -> adminStationService.listDevices());
    }

    @PostMapping("/devices")
    public Result<Map<String, Object>> addDevice(@RequestBody PileRequest request) {
        return Result.of(() -> adminStationService.addDevice(request));
    }

    @PostMapping("/devices/update/{pileId}")
    public Result<Void> updateDevice(@PathVariable String pileId,
                                     @RequestBody PileRequest request) {
        return Result.ofVoid(() -> adminStationService.updateDevice(pileId, request));
    }

    @PostMapping("/devices/delete/{pileId}")
    public Result<Void> deleteDevice(@PathVariable String pileId) {
        return Result.ofVoid(() -> adminStationService.deleteDevice(pileId));
    }
}

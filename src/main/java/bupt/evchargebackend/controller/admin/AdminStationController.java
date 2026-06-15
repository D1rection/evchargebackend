package bupt.evchargebackend.controller.admin;

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
    public Map<String, Object> getStationConfig() {
        return adminStationService.getStationConfig();
    }

    @PostMapping("/station-config")
    public void updateStationConfig(@RequestBody StationConfigRequest request) {
        adminStationService.updateStationConfig(request);
    }

    @GetMapping("/devices")
    public List<Map<String, Object>> listDevices() {
        return adminStationService.listDevices();
    }

    @PostMapping("/devices")
    public Map<String, Object> addDevice(@RequestBody PileRequest request) {
        return adminStationService.addDevice(request);
    }

    @PostMapping("/devices/update/{pileId}")
    public void updateDevice(@PathVariable String pileId,
                             @RequestBody PileRequest request) {
        adminStationService.updateDevice(pileId, request);
    }

    @PostMapping("/devices/delete/{pileId}")
    public void deleteDevice(@PathVariable String pileId) {
        adminStationService.deleteDevice(pileId);
    }
}

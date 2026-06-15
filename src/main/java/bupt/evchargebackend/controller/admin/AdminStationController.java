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

    /** 获取场站全局参数 */
    @GetMapping("/station-config")
    public Result<Map<String, Object>> getStationConfig() {
        return Result.success(adminStationService.getStationConfig());
    }

    /** 更新场站全局参数 */
    @PostMapping("/station-config")
    public Result<Void> updateStationConfig(@RequestBody StationConfigRequest request) {
        adminStationService.updateStationConfig(request);
        return Result.success();
    }

    /** 获取充电桩台账 */
    @GetMapping("/devices")
    public Result<List<Map<String, Object>>> listDevices() {
        return Result.success(adminStationService.listDevices());
    }

    /** 新增充电桩 */
    @PostMapping("/devices")
    public Result<Map<String, Object>> addDevice(@RequestBody PileRequest request) {
        return Result.success(adminStationService.addDevice(request));
    }

    /** 编辑充电桩 */
    @PostMapping("/devices/update/{pileId}")
    public Result<Void> updateDevice(@PathVariable String pileId,
                                     @RequestBody PileRequest request) {
        adminStationService.updateDevice(pileId, request);
        return Result.success();
    }

    /** 删除充电桩 */
    @PostMapping("/devices/delete/{pileId}")
    public Result<Void> deleteDevice(@PathVariable String pileId) {
        adminStationService.deleteDevice(pileId);
        return Result.success();
    }
}

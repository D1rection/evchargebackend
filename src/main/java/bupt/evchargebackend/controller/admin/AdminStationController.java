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

    /**
     * 获取充电站配置：快充/慢充桩数量与单桩等候位数。
     *
     * @return 充电站配置数据（fastCount / slowCount / waitingSpotsPerPile）
     */
    @GetMapping("/station-config")
    public Result<Map<String, Object>> getStationConfig() {
        return adminStationService.getStationConfig();
    }

    /**
     * 更新充电站配置。
     *
     * @param request 配置请求（fastCount / slowCount / waitingSpotsPerPile）
     * @return 空结果
     */
    @PostMapping("/station-config")
    public Result<Void> updateStationConfig(@RequestBody StationConfigRequest request) {
        return adminStationService.updateStationConfig(request);
    }

    /**
     * 查询所有充电桩设备列表。
     *
     * @return 设备列表
     */
    @GetMapping("/devices")
    public Result<List<Map<String, Object>>> listDevices() {
        return adminStationService.listDevices();
    }

    /**
     * 新增充电桩设备。
     *
     * @param request 设备请求（pileNo / pileType / powerKw）
     * @return 包含新设备 pileId 的结果
     */
    @PostMapping("/devices")
    public Result<Map<String, Object>> addDevice(@RequestBody PileRequest request) {
        return adminStationService.addDevice(request);
    }

    /**
     * 修改充电桩设备信息。
     *
     * @param pileId  充电桩ID
     * @param request 设备请求（pileNo / pileType / powerKw，均可选）
     * @return 空结果
     */
    @PostMapping("/devices/update/{pileId}")
    public Result<Void> updateDevice(@PathVariable String pileId,
                                     @RequestBody PileRequest request) {
        return adminStationService.updateDevice(pileId, request);
    }

    /**
     * 删除充电桩设备。
     *
     * @param pileId 充电桩ID
     * @return 空结果
     */
    @PostMapping("/devices/delete/{pileId}")
    public Result<Void> deleteDevice(@PathVariable String pileId) {
        return adminStationService.deleteDevice(pileId);
    }
}

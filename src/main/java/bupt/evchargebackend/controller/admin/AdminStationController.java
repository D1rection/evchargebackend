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
 * <p>
 * 提供场站全局配置管理和充电桩台账的 CRUD 操作。
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

    /**
     * 获取场站全局参数。
     *
     * @return {@code {fastCount, slowCount, waitingSpotsPerPile}} 场站配置
     */
    @GetMapping("/station-config")
    public Result<Map<String, Object>> getStationConfig() {
        return Result.of(() -> adminStationService.getStationConfig());
    }

    /**
     * 更新场站全局参数。
     *
     * @param request 配置请求体，包含 {@code fastCount}、{@code slowCount}、{@code waitingSpotsPerPile}
     * @return 空成功响应
     */
    @PostMapping("/station-config")
    public Result<Void> updateStationConfig(@RequestBody StationConfigRequest request) {
        return Result.ofVoid(() -> adminStationService.updateStationConfig(request));
    }

    /**
     * 获取充电桩台账列表。
     *
     * @return 充电桩列表，每项包含 {@code id}、{@code pileId}、{@code pileNo}、{@code pileType}、{@code powerKw}
     */
    @GetMapping("/devices")
    public Result<List<Map<String, Object>>> listDevices() {
        return Result.of(() -> adminStationService.listDevices());
    }

    /**
     * 新增充电桩。
     *
     * @param request 充电桩请求体，包含 {@code pileNo}、{@code pileType}、{@code powerKw}
     * @return {@code {pileId}} 新创建的充电桩 ID；冲突时 {@code code=409}
     */
    @PostMapping("/devices")
    public Result<Map<String, Object>> addDevice(@RequestBody PileRequest request) {
        return Result.of(() -> adminStationService.addDevice(request));
    }

    /**
     * 编辑充电桩信息。
     *
     * @param pileId  充电桩 ID
     * @param request 充电桩请求体，可部分更新
     * @return 空成功响应；不存在时 {@code code=404}
     */
    @PostMapping("/devices/update/{pileId}")
    public Result<Void> updateDevice(@PathVariable String pileId,
                                     @RequestBody PileRequest request) {
        return Result.ofVoid(() -> adminStationService.updateDevice(pileId, request));
    }

    /**
     * 删除充电桩。
     *
     * @param pileId 充电桩 ID
     * @return 空成功响应；不存在时 {@code code=404}
     */
    @PostMapping("/devices/delete/{pileId}")
    public Result<Void> deleteDevice(@PathVariable String pileId) {
        return Result.ofVoid(() -> adminStationService.deleteDevice(pileId));
    }
}

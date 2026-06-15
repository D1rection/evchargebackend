package bupt.evchargebackend.service.admin;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.PileRequest;
import bupt.evchargebackend.dto.StationConfigRequest;

import java.util.List;
import java.util.Map;

/**
 * 管理员场站设备服务。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
public interface AdminStationService {

    /** 获取场站全局参数 */
    Result<Map<String, Object>> getStationConfig();

    /** 更新场站全局参数 */
    Result<Void> updateStationConfig(StationConfigRequest request);

    /** 获取充电桩台账列表 */
    Result<List<Map<String, Object>>> listDevices();

    /** 新增充电桩，冲突时 {@code code=409} */
    Result<Map<String, Object>> addDevice(PileRequest request);

    /** 编辑充电桩，不存在时 {@code code=404} */
    Result<Void> updateDevice(String pileId, PileRequest request);

    /** 删除充电桩，不存在时 {@code code=404} */
    Result<Void> deleteDevice(String pileId);
}

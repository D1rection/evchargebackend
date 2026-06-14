package bupt.evchargebackend.service.admin;

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
    Map<String, Object> getStationConfig();

    /** 更新场站全局参数 */
    void updateStationConfig(StationConfigRequest request);

    /** 获取充电桩台账列表 */
    List<Map<String, Object>> listDevices();

    /** 新增充电桩，返回 {pileId} */
    Map<String, Object> addDevice(PileRequest request);

    /** 编辑充电桩 */
    void updateDevice(String pileId, PileRequest request);

    /** 删除充电桩 */
    void deleteDevice(String pileId);
}

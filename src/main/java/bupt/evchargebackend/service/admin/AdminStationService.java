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

    /**
     * 获取场站全局参数。
     *
     * @return {@code {fastCount, slowCount, waitingSpotsPerPile}}
     */
    Map<String, Object> getStationConfig();

    /**
     * 更新场站全局参数。
     * <p>
     * 若配置不存在则创建，否则更新。
     *
     * @param request 配置请求，包含快充/慢充桩数量和每桩等候车位数
     */
    void updateStationConfig(StationConfigRequest request);

    /**
     * 获取充电桩台账列表。
     *
     * @return 充电桩列表
     */
    List<Map<String, Object>> listDevices();

    /**
     * 新增充电桩。
     *
     * @param request 充电桩信息，包含 {@code pileNo}、{@code pileType}、{@code powerKw}
     * @return {@code {pileId}} 新创建的充电桩 ID
     */
    Map<String, Object> addDevice(PileRequest request);

    /**
     * 编辑充电桩信息，支持部分更新。
     *
     * @param pileId  充电桩 ID
     * @param request 更新的字段
     * @throws bupt.evchargebackend.common.exception.BusinessException 充电桩不存在时抛出（404）
     */
    void updateDevice(String pileId, PileRequest request);

    /**
     * 删除充电桩。
     *
     * @param pileId 充电桩 ID
     * @throws bupt.evchargebackend.common.exception.BusinessException 充电桩不存在时抛出（404）
     */
    void deleteDevice(String pileId);
}

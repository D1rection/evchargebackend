package bupt.evchargebackend.service.admin.impl;

import bupt.evchargebackend.common.exception.BusinessException;
import bupt.evchargebackend.dto.PileRequest;
import bupt.evchargebackend.dto.StationConfigRequest;
import bupt.evchargebackend.entity.pile.ChargingPile;
import bupt.evchargebackend.entity.pile.enums.PileType;
import bupt.evchargebackend.entity.pile.enums.PowerState;
import bupt.evchargebackend.entity.pile.enums.WorkingState;
import bupt.evchargebackend.entity.station.StationConfig;
import bupt.evchargebackend.mapper.pile.ChargingPileMapper;
import bupt.evchargebackend.mapper.station.StationConfigMapper;
import bupt.evchargebackend.service.admin.AdminStationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 管理员场站设备服务实现。
 * <p>
 * 使用 MyBatis Plus 管理场站全局配置和充电桩台账的 CRUD 操作。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@Service
public class AdminStationServiceImpl implements AdminStationService {

    private final StationConfigMapper stationConfigMapper;
    private final ChargingPileMapper chargingPileMapper;

    public AdminStationServiceImpl(StationConfigMapper stationConfigMapper,
                                   ChargingPileMapper chargingPileMapper) {
        this.stationConfigMapper = stationConfigMapper;
        this.chargingPileMapper = chargingPileMapper;
    }

    /**
     * 获取场站全局参数。
     * <p>
     * 若数据库中无配置记录，返回默认值。
     *
     * @return {@code {fastCount, slowCount, waitingSpotsPerPile}}
     */
    @Override
    public Map<String, Object> getStationConfig() {
        List<StationConfig> configs = stationConfigMapper.selectList(null);
        if (configs.isEmpty()) {
            return Map.of("fastCount", 0, "slowCount", 0, "waitingSpotsPerPile", 2);
        }
        StationConfig config = configs.get(0);
        return Map.of("fastCount", config.getFastCount(),
                      "slowCount", config.getSlowCount(),
                      "waitingSpotsPerPile", config.getWaitingSpotsPerPile());
    }

    /**
     * 更新场站全局参数。
     * <p>
     * 采用 upsert 策略：若配置不存在则插入，否则更新。
     *
     * @param request 配置请求，包含快充/慢充桩数量和每桩等候车位数
     */
    @Override
    public void updateStationConfig(StationConfigRequest request) {
        List<StationConfig> configs = stationConfigMapper.selectList(null);
        StationConfig config;
        if (configs.isEmpty()) {
            config = new StationConfig();
        } else {
            config = configs.get(0);
        }
        config.setFastCount(request.getFastCount());
        config.setSlowCount(request.getSlowCount());
        config.setWaitingSpotsPerPile(request.getWaitingSpotsPerPile());
        if (config.getId() == null) {
            stationConfigMapper.insert(config);
        } else {
            stationConfigMapper.updateById(config);
        }
    }

    /**
     * 获取充电桩台账列表。
     *
     * @return 充电桩列表，每项含 {@code id}、{@code pileId}、{@code pileNo}、{@code pileType}、{@code powerKw}
     */
    @Override
    public List<Map<String, Object>> listDevices() {
        List<ChargingPile> piles = chargingPileMapper.selectList(null);
        List<Map<String, Object>> result = new ArrayList<>();
        int index = 1;
        for (ChargingPile pile : piles) {
            result.add(Map.of("id", index++,
                              "pileId", pile.getPileId(),
                              "pileNo", pile.getPileNo(),
                              "pileType", pile.getPileType().name(),
                              "powerKw", pile.getPowerKw()));
        }
        return result;
    }

    /**
     * 新增充电桩。
     * <p>
     * 自动生成桩 ID（FP=快充 / SP=慢充 + 3 位随机大写字母），
     * 初始状态为停机（OFF/STOPPED），累计量归零。
     *
     * @param request 充电桩信息，包含 {@code pileNo}、{@code pileType}、{@code powerKw}
     * @return {@code {pileId}} 新创建的充电桩 ID
     * @throws BusinessException 桩 ID 冲突时抛出（409）
     */
    @Override
    public Map<String, Object> addDevice(PileRequest request) {
        String type = request.getPileType().toUpperCase();
        String prefix = type.equals("FAST") ? "FP" : "SP";
        String suffix = UUID.randomUUID().toString().substring(0, 3).toUpperCase();
        String pileId = prefix + suffix;

        LambdaQueryWrapper<ChargingPile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChargingPile::getPileId, pileId);
        if (chargingPileMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(409, "充电桩ID已存在");
        }

        ChargingPile pile = new ChargingPile();
        pile.setPileId(pileId);
        pile.setPileNo(request.getPileNo());
        pile.setPileType(PileType.valueOf(type));
        pile.setPowerKw(request.getPowerKw());
        pile.setPowerState(PowerState.OFF);
        pile.setWorkingState(WorkingState.STOPPED);
        pile.setTotalChargeKwh(java.math.BigDecimal.ZERO);
        pile.setTotalChargeCount(0);
        pile.setTotalChargeMinutes(0);
        chargingPileMapper.insert(pile);

        return Map.of("pileId", pileId);
    }

    /**
     * 编辑充电桩信息，支持部分更新。
     *
     * @param pileId  充电桩 ID
     * @param request 更新的字段（{@code pileNo}、{@code pileType}、{@code powerKw} 可部分传入）
     * @throws BusinessException 充电桩不存在时抛出（404）
     */
    @Override
    public void updateDevice(String pileId, PileRequest request) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) {
            throw new BusinessException(404, "充电桩不存在");
        }
        if (request.getPileNo() != null) {
            pile.setPileNo(request.getPileNo());
        }
        if (request.getPileType() != null) {
            pile.setPileType(PileType.valueOf(request.getPileType().toUpperCase()));
        }
        if (request.getPowerKw() != null) {
            pile.setPowerKw(request.getPowerKw());
        }
        chargingPileMapper.updateById(pile);
    }

    /**
     * 删除充电桩。
     *
     * @param pileId 充电桩 ID
     * @throws BusinessException 充电桩不存在时抛出（404）
     */
    @Override
    public void deleteDevice(String pileId) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) {
            throw new BusinessException(404, "充电桩不存在");
        }
        chargingPileMapper.deleteById(pileId);
    }
}

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

    @Override
    public Map<String, Object> getStationConfig() {
        List<StationConfig> configs = stationConfigMapper.selectList(null);
        if (configs.isEmpty()) {
            // 如果没有配置，返回默认值
            return Map.of("fastCount", 0, "slowCount", 0, "waitingSpotsPerPile", 2);
        }
        StationConfig config = configs.get(0);
        return Map.of("fastCount", config.getFastCount(),
                      "slowCount", config.getSlowCount(),
                      "waitingSpotsPerPile", config.getWaitingSpotsPerPile());
    }

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

    @Override
    public Map<String, Object> addDevice(PileRequest request) {
        // 生成 pileId 后缀生成规则为随机字母大写(也行吧)
        String type = request.getPileType().toUpperCase();
        String prefix = type.equals("FAST") ? "FP" : "SP";
        String suffix = UUID.randomUUID().toString().substring(0, 3).toUpperCase();
        String pileId = prefix + suffix;

        // 检查唯一性
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

    @Override
    public void deleteDevice(String pileId) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) {
            throw new BusinessException(404, "充电桩不存在");
        }
        chargingPileMapper.deleteById(pileId);
    }
}

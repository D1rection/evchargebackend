package bupt.evchargebackend.service.admin.impl;

import bupt.evchargebackend.common.response.Result;
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

import java.math.BigDecimal;
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
    public Result<Map<String, Object>> getStationConfig() {
        List<StationConfig> configs = stationConfigMapper.selectList(null);
        if (configs.isEmpty()) {
            return Result.success(Map.of("fastCount", 0, "slowCount", 0, "waitingSpotsPerPile", 2));
        }
        StationConfig config = configs.get(0);
        return Result.success(Map.of("fastCount", config.getFastCount(),
                                     "slowCount", config.getSlowCount(),
                                     "waitingSpotsPerPile", config.getWaitingSpotsPerPile()));
    }

    @Override
    public Result<Void> updateStationConfig(StationConfigRequest request) {
        List<StationConfig> configs = stationConfigMapper.selectList(null);
        StationConfig config = configs.isEmpty() ? new StationConfig() : configs.get(0);
        config.setFastCount(request.getFastCount());
        config.setSlowCount(request.getSlowCount());
        config.setWaitingSpotsPerPile(request.getWaitingSpotsPerPile());
        if (config.getId() == null) {
            stationConfigMapper.insert(config);
        } else {
            stationConfigMapper.updateById(config);
        }
        return Result.success();
    }

    @Override
    public Result<List<Map<String, Object>>> listDevices() {
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
        return Result.success(result);
    }

    @Override
    public Result<Map<String, Object>> addDevice(PileRequest request) {
        String type = request.getPileType().toUpperCase();
        String prefix = type.equals("FAST") ? "FP" : "SP";
        String suffix = UUID.randomUUID().toString().substring(0, 3).toUpperCase();
        String pileId = prefix + suffix;

        LambdaQueryWrapper<ChargingPile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChargingPile::getPileId, pileId);
        if (chargingPileMapper.selectCount(wrapper) > 0) {
            return Result.error(409, "充电桩ID已存在");
        }

        ChargingPile pile = new ChargingPile();
        pile.setPileId(pileId);
        pile.setPileNo(request.getPileNo());
        pile.setPileType(PileType.valueOf(type));
        pile.setPowerKw(request.getPowerKw());
        pile.setPowerState(PowerState.OFF);
        pile.setWorkingState(WorkingState.STOPPED);
        pile.setTotalChargeKwh(BigDecimal.ZERO);
        pile.setTotalChargeCount(0);
        pile.setTotalChargeMinutes(0);
        chargingPileMapper.insert(pile);

        return Result.success(Map.of("pileId", pileId));
    }

    @Override
    public Result<Void> updateDevice(String pileId, PileRequest request) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) {
            return Result.error(404, "充电桩不存在");
        }
        if (request.getPileNo() != null) pile.setPileNo(request.getPileNo());
        if (request.getPileType() != null)
            pile.setPileType(PileType.valueOf(request.getPileType().toUpperCase()));
        if (request.getPowerKw() != null) pile.setPowerKw(request.getPowerKw());
        chargingPileMapper.updateById(pile);
        return Result.success();
    }

    @Override
    public Result<Void> deleteDevice(String pileId) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) {
            return Result.error(404, "充电桩不存在");
        }
        chargingPileMapper.deleteById(pileId);
        return Result.success();
    }
}

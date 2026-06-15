package bupt.evchargebackend.service.pile.impl;

import bupt.evchargebackend.common.exception.BusinessException;
import bupt.evchargebackend.common.exception.ErrorCode;
import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.pile.PileQueueItem;
import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.charging.ChargingSession;
import bupt.evchargebackend.entity.charging.enums.SessionStatus;
import bupt.evchargebackend.entity.pile.ChargingPile;
import bupt.evchargebackend.entity.pile.enums.PowerState;
import bupt.evchargebackend.entity.pile.enums.WorkingState;
import bupt.evchargebackend.entity.user.Car;
import bupt.evchargebackend.mapper.charging.ChargingSessionMapper;
import bupt.evchargebackend.mapper.pile.ChargingPileMapper;
import bupt.evchargebackend.mapper.user.CarMapper;
import bupt.evchargebackend.service.pile.PileService;
import bupt.evchargebackend.service.schedule.SchedulingEngine;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class PileServiceImpl implements PileService {

    private final ChargingPileMapper chargingPileMapper;
    private final SchedulingEngine engine;
    private final ChargingSessionMapper chargingSessionMapper;
    private final CarMapper carMapper;

    public PileServiceImpl(ChargingPileMapper chargingPileMapper,
                           SchedulingEngine engine,
                           ChargingSessionMapper chargingSessionMapper,
                           CarMapper carMapper) {
        this.chargingPileMapper = chargingPileMapper;
        this.engine = engine;
        this.chargingSessionMapper = chargingSessionMapper;
        this.carMapper = carMapper;
    }

    @Override
    public List<ChargingPile> listPileStates(String pileId) {
        if (pileId == null || pileId.isBlank()) {
            return chargingPileMapper.selectList(null);
        }
        return chargingPileMapper.selectList(
                new LambdaQueryWrapper<ChargingPile>()
                        .eq(ChargingPile::getPileId, pileId)
        );
    }

    @Override
    public ChargingPile powerOn(String pileId) {
        ChargingPile pile = requirePile(pileId);
        if (pile.getPowerState() == PowerState.ON) {
            throw new BusinessException(ErrorCode.PILE_ALREADY_RUNNING);
        }

        pile.setPowerState(PowerState.ON);
        pile.setWorkingState(WorkingState.STOPPED);
        chargingPileMapper.updateById(pile);
        return pile;
    }

    @Override
    public ChargingPile powerOff(String pileId) {
        ChargingPile pile = requirePile(pileId);
        if (pile.getPowerState() == PowerState.OFF && pile.getWorkingState() == WorkingState.STOPPED) {
            throw new BusinessException(ErrorCode.PILE_ALREADY_STOPPED);
        }
        if (pile.getWorkingState() == WorkingState.CHARGING) {
            throw new BusinessException(ErrorCode.OPERATION_INVALID);
        }

        pile.setPowerState(PowerState.OFF);
        pile.setWorkingState(WorkingState.STOPPED);
        pile.setCurrentSessionId(null);
        chargingPileMapper.updateById(pile);
        return pile;
    }

    @Override
    public ChargingPile start(String pileId) {
        ChargingPile pile = requirePile(pileId);
        if (pile.getPowerState() != PowerState.ON) {
            throw new BusinessException(ErrorCode.OPERATION_INVALID);
        }
        if (pile.getWorkingState() == WorkingState.AVAILABLE) {
            throw new BusinessException(ErrorCode.PILE_ALREADY_RUNNING);
        }
        if (pile.getWorkingState() == WorkingState.CHARGING || pile.getWorkingState() == WorkingState.FAULT) {
            throw new BusinessException(ErrorCode.OPERATION_INVALID);
        }

        pile.setWorkingState(WorkingState.AVAILABLE);
        chargingPileMapper.updateById(pile);
        return pile;
    }

    @Override
    public Result<List<PileQueueItem>> getPileQueue(String pileId) {
        // 1. 校验桩存在
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) {
            return Result.error(404, "充电桩不存在");
        }

        // 2. 获取队列
        List<PileQueueItem> items = new ArrayList<>();
        BigDecimal power = BigDecimal.valueOf(pile.getPowerKw());
        ChargingOrder head = engine.peekPileQueue(pileId);
        List<ChargingOrder> waiting = engine.getPileQueue(pileId);

        // 3. 遍历队列，查车辆信息并算等待时间
        long headRemainSec = 0;
        if (head != null) {
            Car car = carMapper.selectById(head.getCarId());
            ChargingSession session = chargingSessionMapper.selectOne(
                    new QueryWrapper<ChargingSession>()
                            .eq("pile_id", pileId)
                            .eq("session_status", SessionStatus.CHARGING)
                            .orderByDesc("created_at").last("LIMIT 1")
            );
            if (session != null) {
                BigDecimal remain = session.getTargetKwh().subtract(session.getChargedKwh());
                if (remain.compareTo(BigDecimal.ZERO) > 0) {
                    headRemainSec = remain.divide(power, 10, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(3600)).longValue();
                }
            }
            if (car != null) {
                PileQueueItem item = new PileQueueItem();
                item.setCarId(head.getCarId());
                item.setCarCapacity(car.getBatteryCapacityKwh());
                item.setRequestAmount(head.getTargetKwh());
                item.setQueuePosition(1);
                item.setWaitTime(formatDuration(headRemainSec));
                items.add(item);
            }
        }

        long accumulated = headRemainSec;
        for (int i = 0; i < waiting.size(); i++) {
            Car car = carMapper.selectById(waiting.get(i).getCarId());
            if (car != null) {
                PileQueueItem item = new PileQueueItem();
                ChargingOrder order = waiting.get(i);
                item.setCarId(order.getCarId());
                item.setCarCapacity(car.getBatteryCapacityKwh());
                item.setRequestAmount(order.getTargetKwh());
                item.setQueuePosition(i + 2);
                item.setWaitTime(formatDuration(accumulated));
                items.add(item);
            }
            long chargeSec = waiting.get(i).getTargetKwh()
                    .divide(power, 10, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(3600)).longValue();
            accumulated += chargeSec;
        }

        return Result.success(items);
    }

    private static String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private ChargingPile requirePile(String pileId) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return pile;
    }
}

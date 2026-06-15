package bupt.evchargebackend.service.pile.impl;

import bupt.evchargebackend.common.exception.BusinessException;
import bupt.evchargebackend.common.exception.ErrorCode;
import bupt.evchargebackend.entity.pile.ChargingPile;
import bupt.evchargebackend.entity.pile.enums.PowerState;
import bupt.evchargebackend.entity.pile.enums.WorkingState;
import bupt.evchargebackend.mapper.pile.ChargingPileMapper;
import bupt.evchargebackend.service.pile.PileService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PileServiceImpl implements PileService {

    private final ChargingPileMapper chargingPileMapper;

    public PileServiceImpl(ChargingPileMapper chargingPileMapper) {
        this.chargingPileMapper = chargingPileMapper;
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

    private ChargingPile requirePile(String pileId) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return pile;
    }
}

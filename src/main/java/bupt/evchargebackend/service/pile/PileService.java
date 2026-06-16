package bupt.evchargebackend.service.pile;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.pile.PileQueueItem;
import bupt.evchargebackend.entity.pile.ChargingPile;

import java.util.List;

public interface PileService {

    List<ChargingPile> listPileStates(String pileId);

    /**
     * 查看充电桩前排队：返回当前充电车辆 + 等待车辆列表，附带预计等待时间。
     *
     * @param pileId 充电桩 ID
     * @return 排队列表，含车辆信息、充电量和等待时间
     */
    Result<List<PileQueueItem>> getPileQueue(String pileId);

    ChargingPile powerOn(String pileId);

    ChargingPile powerOff(String pileId);

    ChargingPile start(String pileId);

    /** 触发桩故障：中断充电（如有）、移入故障队列、设置 FAULT 状态。 */
    void triggerFault(String pileId);

    /** 恢复桩：设置 AVAILABLE、更新故障记录、分发故障队列。 */
    void recoverFault(String pileId);
}

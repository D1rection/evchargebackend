package bupt.evchargebackend.service.schedule.event;

import bupt.evchargebackend.entity.pile.enums.PileType;

/**
 * 充电桩释放事件，表示一个桩位变为可用。
 *
 * @param pileId   释放的充电桩 ID
 * @param pileType 桩类型（快充／慢充），用于确定调度哪个队列
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
public record PileReleasedEvent(String pileId, PileType pileType) {
}

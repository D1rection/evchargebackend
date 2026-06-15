package bupt.evchargebackend.service.schedule.event;

import bupt.evchargebackend.entity.pile.enums.PileType;

/**
 * 充电桩故障恢复事件，表示一个故障桩恢复可用。
 *
 * @param pileId   恢复的充电桩 ID
 * @param pileType 桩类型（快充／慢充）
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
public record PileRecoveredEvent(String pileId, PileType pileType) {
}

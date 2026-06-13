package bupt.evchargebackend.entity.pile.enums;

/**
 * 运行状态枚举。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
public enum WorkingState {
    /** 空闲可用 */
    AVAILABLE,
    /** 充电中 */
    CHARGING,
    /** 故障 */
    FAULT,
    /** 停机 */
    STOPPED
}

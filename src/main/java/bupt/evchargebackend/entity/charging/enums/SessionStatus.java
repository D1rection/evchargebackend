package bupt.evchargebackend.entity.charging.enums;

/**
 * 充电过程状态枚举。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
public enum SessionStatus {
    /** 充电中 */
    CHARGING,
    /** 已完成 */
    FINISHED,
    /** 异常中断 */
    INTERRUPTED
}

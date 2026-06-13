package bupt.evchargebackend.entity.charging.enums;

/**
 * 充电订单状态枚举。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
public enum OrderStatus {
    /** 等待中 */
    WAITING,
    /** 已叫号 */
    CALLED,
    /** 充电中 */
    CHARGING,
    /** 已完成 */
    FINISHED,
    /** 已取消 */
    CANCELLED
}

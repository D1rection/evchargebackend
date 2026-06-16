package bupt.evchargebackend.common.time;

import java.time.LocalDateTime;

/**
 * 时间抽象接口，使调度与计费引擎可脱离系统时钟运行。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
public interface TimeProvider {

    /** 返回当前时间。 */
    LocalDateTime now();

    /** 当前是否为模拟时间模式（默认 false）。 */
    default boolean isSimulating() { return false; }
}

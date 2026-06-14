package bupt.evchargebackend.stub;

import bupt.evchargebackend.common.time.TimeProvider;

import java.time.LocalDateTime;

/**
 * 模拟时间提供者，Driver 步进控制。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
public class SimulatedTimeProvider implements TimeProvider {

    private LocalDateTime now;

    /** @param start 模拟起始时间 */
    public SimulatedTimeProvider(LocalDateTime start) {
        this.now = start;
    }

    @Override
    public LocalDateTime now() {
        return now;
    }

    /** 推进模拟时间（分钟）。 */
    public void advance(int minutes) {
        now = now.plusMinutes(minutes);
    }
}

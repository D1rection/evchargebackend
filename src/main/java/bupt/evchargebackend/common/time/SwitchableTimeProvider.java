package bupt.evchargebackend.common.time;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

/**
 * 可切换的 TimeProvider 代理。生产环境使用真实时间，模拟时切换到模拟时间。
 *
 * @author Deng Chao
 * @since 2026-06-17
 */
@Component
@Primary
public class SwitchableTimeProvider implements TimeProvider {

    private TimeProvider delegate = new SystemTimeProvider();

    @Override
    public LocalDateTime now() {
        return delegate.now();
    }

    /** 切换到模拟时间，设定起始时间。 */
    public void startSimulation(LocalDateTime startTime) {
        delegate = new SimulatedTimeProvider(startTime);
    }

    /** 停止模拟，切回真实时间。 */
    public void stopSimulation() {
        delegate = new SystemTimeProvider();
    }

    /** 推进模拟时间（分钟），非模拟模式无效果。 */
    public void advance(int minutes) {
        if (delegate instanceof SimulatedTimeProvider tp) {
            tp.advance(minutes);
        }
    }

    /** 当前是否为模拟模式。 */
    @Override
    public boolean isSimulating() {
        return delegate instanceof SimulatedTimeProvider;
    }
}

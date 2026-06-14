package bupt.evchargebackend.common.time;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 生产环境时间提供者，返回真实系统时钟。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
@Component
public class SystemTimeProvider implements TimeProvider {

    @Override
    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}

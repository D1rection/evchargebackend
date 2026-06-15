package bupt.evchargebackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 充电定时任务线程池：用于充满自动结束充电。
 *
 * @author Deng Chao
 * @since 2026-06-16
 */
@Configuration
public class SchedulingConfig {

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService chargeScheduler() {
        return Executors.newSingleThreadScheduledExecutor();
    }
}

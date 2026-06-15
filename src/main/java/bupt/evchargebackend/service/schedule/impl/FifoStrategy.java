package bupt.evchargebackend.service.schedule.impl;

import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.service.schedule.ScheduleStrategy;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * FIFO 调度策略：取等待队列中最早到达的车辆。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
@Component
public class FifoStrategy implements ScheduleStrategy {

    @Override
    public ChargingOrder selectOne(List<ChargingOrder> candidates) {
        return candidates.getFirst();
    }
}

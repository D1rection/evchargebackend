package bupt.evchargebackend.config;

import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.queue.QueueEntry;
import bupt.evchargebackend.mapper.charging.ChargingOrderMapper;
import bupt.evchargebackend.mapper.queue.QueueEntryMapper;
import bupt.evchargebackend.service.schedule.SchedulingEngine;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动时从 queue_entry 表重建 SchedulingEngine 内存队列。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@Component
public class QueueInitializer {

    private static final Logger log = LoggerFactory.getLogger(QueueInitializer.class);

    private final QueueEntryMapper queueEntryMapper;
    private final ChargingOrderMapper chargingOrderMapper;
    private final SchedulingEngine engine;

    public QueueInitializer(QueueEntryMapper queueEntryMapper,
                            ChargingOrderMapper chargingOrderMapper,
                            SchedulingEngine engine) {
        this.queueEntryMapper = queueEntryMapper;
        this.chargingOrderMapper = chargingOrderMapper;
        this.engine = engine;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuild() {
        log.info("开始重建内存队列...");
        List<QueueEntry> entries = queueEntryMapper.selectList(
                new QueryWrapper<QueueEntry>().orderByAsc("id")
        );

        List<ChargingOrder> fastWait = new ArrayList<>();
        List<ChargingOrder> slowWait = new ArrayList<>();
        Map<String, List<ChargingOrder>> pileOrders = new HashMap<>();

        for (QueueEntry entry : entries) {
            ChargingOrder order = chargingOrderMapper.selectById(entry.getOrderId());
            if (order == null) continue;

            switch (entry.getQueueType()) {
                case "WAIT" -> {
                    if ("FAST".equals(entry.getQueueKey())) fastWait.add(order);
                    else slowWait.add(order);
                }
                case "PILE" -> pileOrders.computeIfAbsent(entry.getQueueKey(), k -> new ArrayList<>()).add(order);
            }
        }

        engine.rebuild(fastWait, slowWait, pileOrders);
        log.info("内存队列重建完成：等候区{}辆，桩队列{}条",
                fastWait.size() + slowWait.size(),
                pileOrders.values().stream().mapToInt(List::size).sum());
    }
}

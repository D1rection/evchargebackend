package bupt.evchargebackend.service.queue.impl;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.queue.WaitingQueueItem;
import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.user.Car;
import bupt.evchargebackend.mapper.user.CarMapper;
import bupt.evchargebackend.service.queue.QueueService;
import bupt.evchargebackend.common.time.TimeProvider;
import bupt.evchargebackend.service.schedule.SchedulingEngine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

@Service
public class QueueServiceImpl implements QueueService {

    private final SchedulingEngine engine;
    private final CarMapper carMapper;
    private final TimeProvider timeProvider;

    public QueueServiceImpl(SchedulingEngine engine, CarMapper carMapper, TimeProvider timeProvider) {
        this.engine = engine;
        this.carMapper = carMapper;
        this.timeProvider = timeProvider;
    }

    @Override
    public Result<List<WaitingQueueItem>> getWaitingQueue(String queueMode) {
        // 1. 校验 queueMode
        if (queueMode == null || queueMode.isBlank()) {
            return Result.error(400, "queueMode 不能为空");
        }
        String mode = queueMode.trim().toLowerCase();
        if (!"fast".equals(mode) && !"slow".equals(mode) && !"all".equals(mode)) {
            return Result.error(400, "queueMode 必须为 fast / slow / all");
        }

        // 2. 取数据
        List<ChargingOrder> orders;
        if ("all".equals(mode)) {
            orders = mergeQueues(engine.getFastWaitQueue(), engine.getSlowWaitQueue());
        } else if ("fast".equals(mode)) {
            orders = new ArrayList<>(engine.getFastWaitQueue());
        } else {
            orders = new ArrayList<>(engine.getSlowWaitQueue());
        }

        // 3. 遍历 + 组装
        LocalDateTime now = timeProvider.now();
        List<WaitingQueueItem> items = new ArrayList<>();
        for (ChargingOrder order : orders) {
            Car car = carMapper.selectById(order.getCarId());
            if (car == null) continue;

            WaitingQueueItem item = new WaitingQueueItem();
            item.setCarId(order.getCarId());
            item.setCarCapacity(car.getBatteryCapacityKwh());
            item.setRequestAmount(order.getTargetKwh());
            if (order.getCreatedAt() != null) {
                long sec = Duration.between(order.getCreatedAt(), now).getSeconds();
                item.setWaitTime(formatDuration(Math.max(0, sec)));
            } else {
                item.setWaitTime("00:00:00");
            }
            item.setRequestMode(order.getRequestMode().name());
            items.add(item);
        }
        return Result.success(items);
    }

    private static String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    /** 合并快慢等候区，按 createdAt 归并排序。 */
    private static List<ChargingOrder> mergeQueues(Queue<ChargingOrder> fast, Queue<ChargingOrder> slow) {
        List<ChargingOrder> a = new ArrayList<>(fast);
        List<ChargingOrder> b = new ArrayList<>(slow);
        List<ChargingOrder> result = new ArrayList<>(a.size() + b.size());
        int i = 0, j = 0;
        while (i < a.size() || j < b.size()) {
            if (i >= a.size()) {
                result.add(b.get(j++));
            } else if (j >= b.size()) {
                result.add(a.get(i++));
            } else if (a.get(i).getCreatedAt() != null
                    && (b.get(j).getCreatedAt() == null
                        || !a.get(i).getCreatedAt().isAfter(b.get(j).getCreatedAt()))) {
                result.add(a.get(i++));
            } else {
                result.add(b.get(j++));
            }
        }
        return result;
    }
}

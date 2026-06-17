package bupt.evchargebackend.service.schedule;

import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.charging.enums.RequestMode;
import bupt.evchargebackend.entity.pile.enums.PileType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 调度引擎：管理等候区、桩前队列。
 *
 * 等候区（快/慢）— 全满时进入，故障期间冻结
 * 桩前队列（每桩一条）— position 0 充电中，position 1+ 排队，M 上限（默认 3）
 *
 * 故障不影响物理队列——故障桩的排队车仍留在原桩 deque，通过标记实现优先级调度。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
@Component
public class SchedulingEngine {

    private static final int DEFAULT_MAX_QUEUE = 3;

    private final ScheduleStrategy scheduleStrategy;
    private final int maxPileQueue;

    private final Queue<ChargingOrder> fastWaitQueue = new LinkedList<>();
    private final Queue<ChargingOrder> slowWaitQueue = new LinkedList<>();
    private final Map<String, Deque<ChargingOrder>> pileQueues = new ConcurrentHashMap<>();
    private final Set<String> faultedPiles = new HashSet<>();

    @Autowired
    public SchedulingEngine(ScheduleStrategy scheduleStrategy) {
        this(scheduleStrategy, DEFAULT_MAX_QUEUE);
    }

    public SchedulingEngine(ScheduleStrategy scheduleStrategy, int maxPileQueue) {
        this.scheduleStrategy = scheduleStrategy;
        this.maxPileQueue = maxPileQueue;
    }

    // ---- 等候区 ----

    public void enqueueWait(ChargingOrder order) {
        waitQueue(toPileType(order.getRequestMode())).add(order);
    }

    public int waitQueueSize(PileType type) {
        return waitQueue(type).size();
    }

    /** 查找指定订单在等候区中的位置（0-based），-1 表示不在队列中。 */
    public int waitPosition(PileType type, String orderId) {
        int i = 0;
        for (var o : waitQueue(type)) {
            if (o.getOrderId().equals(orderId)) return i;
            i++;
        }
        return -1;
    }

    public ChargingOrder pollWait(PileType type) {
        return waitQueue(type).poll();
    }

    public int totalWaitSize() {
        return fastWaitQueue.size() + slowWaitQueue.size();
    }

    // ---- 故障标记 ----

    public void markFault(String pileId) {
        faultedPiles.add(pileId);
    }

    public void clearFault(String pileId) {
        faultedPiles.remove(pileId);
    }

    public boolean hasAnyFault() {
        return !faultedPiles.isEmpty();
    }

    /** 桩队列中首个订单（充电中）。 */
    public ChargingOrder peekPileQueueHead(String pileId) {
        return pileQueue(pileId).peekFirst();
    }

    /** 从桩队列头部移除订单（用于故障期间将故障桩的排队车调走）。 */
    public ChargingOrder pollFromPileQueueHead(String pileId) {
        return pileQueue(pileId).pollFirst();
    }

    // ---- 桩前队列 ----

    public int pileQueueSize(String pileId) {
        return pileQueue(pileId).size();
    }

    /** 查找指定订单在桩队列中的位置（0-based），-1 表示不在队列中。 */
    public int pilePosition(String pileId, String orderId) {
        int i = 0;
        for (var o : pileQueue(pileId)) {
            if (o.getOrderId().equals(orderId)) return i;
            i++;
        }
        return -1;
    }

    public List<ChargingOrder> getPileQueue(String pileId) {
        Deque<ChargingOrder> pq = pileQueue(pileId);
        if (pq.isEmpty()) return List.of();
        var copy = new LinkedList<>(pq);
        copy.pollFirst(); // 排除 position 0（充电中）
        return List.copyOf(copy);
    }

    /** 查看桩队列 position 0（充电中或即将充电的订单）。 */
    public ChargingOrder peekPileQueue(String pileId) {
        return pileQueue(pileId).peekFirst();
    }

    public boolean addToPileQueue(String pileId, ChargingOrder order) {
        Deque<ChargingOrder> pq = pileQueue(pileId);
        if (pq.size() >= maxPileQueue) return false;
        return pq.add(order);
    }

    /** 将即将充电的订单设为 position 0（已在 position 0 则跳过）。 */
    public void setCharging(String pileId, ChargingOrder order) {
        Deque<ChargingOrder> pq = pileQueue(pileId);
        ChargingOrder first = pq.peekFirst();
        if (first != null && first.getOrderId().equals(order.getOrderId())) return;
        pq.addFirst(order);
    }

    // ---- 事件处理 ----

    /**
     * 充电桩完成一轮充电时触发：
     * 1. position 0 出队（已完成的订单）
     * 2. 原 position 1 顶上 → 新的 position 0
     * <p>等候区补位由 Service 层 {@code tryFillFromWaiting} 处理。</p>
     */
    public Optional<ChargingOrder> onPileReleased(String pileId, PileType pileType) {
        Deque<ChargingOrder> pq = pileQueue(pileId);
        pq.pollFirst();
        return Optional.ofNullable(pq.peekFirst());
    }

    /** 从等候区移除指定车辆的订单。 */
    public boolean removeFromWait(String carId) {
        return fastWaitQueue.removeIf(o -> carId.equals(o.getCarId()))
                || slowWaitQueue.removeIf(o -> carId.equals(o.getCarId()));
    }

    public void removeFromAllPileQueues(String carId) {
        for (var pq : pileQueues.values()) {
            pq.removeIf(o -> carId.equals(o.getCarId()));
        }
    }

    public java.util.Queue<ChargingOrder> getFastWaitQueue() { return fastWaitQueue; }
    public java.util.Queue<ChargingOrder> getSlowWaitQueue() { return slowWaitQueue; }

    // ---- 重建 ----

    /**
     * 清空并重建所有内存队列（启动时从 queue_entry 恢复后调用）。
     */
    public void rebuild(List<ChargingOrder> fastWait, List<ChargingOrder> slowWait,
                        Map<String, List<ChargingOrder>> pileOrders) {
        fastWaitQueue.clear();
        slowWaitQueue.clear();
        pileQueues.clear();
        faultedPiles.clear();

        fastWaitQueue.addAll(fastWait);
        slowWaitQueue.addAll(slowWait);

        for (var entry : pileOrders.entrySet()) {
            Deque<ChargingOrder> pq = pileQueue(entry.getKey());
            pq.addAll(entry.getValue());
        }
    }

    // ---- 内部 ----

    private static PileType toPileType(RequestMode mode) {
        return mode == RequestMode.FAST ? PileType.FAST : PileType.SLOW;
    }

    private Queue<ChargingOrder> waitQueue(PileType type) {
        return type == PileType.FAST ? fastWaitQueue : slowWaitQueue;
    }

    private Deque<ChargingOrder> pileQueue(String pileId) {
        return pileQueues.computeIfAbsent(pileId, k -> new LinkedList<>());
    }
}

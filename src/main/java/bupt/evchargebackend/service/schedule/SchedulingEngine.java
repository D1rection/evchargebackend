package bupt.evchargebackend.service.schedule;

import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.charging.enums.RequestMode;
import bupt.evchargebackend.entity.pile.enums.PileType;
import bupt.evchargebackend.service.schedule.event.PileRecoveredEvent;
import bupt.evchargebackend.service.schedule.event.PileReleasedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 调度引擎：管理等候区、故障队列、桩前队列。
 *
 * 等候区（快/慢）— 全满时进入，故障期间冻结
 * 故障队列（快/慢）— 故障车暂存，恢复后分发
 * 桩前队列（每桩一条）— position 0 充电中，M 上限（默认 3）
 *
 * onPileReleased — 充电完成 → 排队下一辆顶上 → 等候区填补
 * onPileFaulted — 桩故障 → 排队车移入故障队列（保留充电中车辆）
 *
 * 故障车由 Service 层 redistributeFaults() 分发
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
    private final Queue<ChargingOrder> fastFaultQueue = new LinkedList<>();
    private final Queue<ChargingOrder> slowFaultQueue = new LinkedList<>();
    private final Map<String, Deque<ChargingOrder>> pileQueues = new ConcurrentHashMap<>();
    /** 抑制fillSlot从等候区分发（边界步进有待处理故障时暂缓，避免故障前一刻误分）。 */
    private boolean deferWaitDispatch = false;

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

    public ChargingOrder pollWait(PileType type) {
        return waitQueue(type).poll();
    }

    public int totalWaitSize() {
        return fastWaitQueue.size() + slowWaitQueue.size();
    }

    // ---- 故障队列 ----

    public void enqueueFault(ChargingOrder order) {
        faultQueue(toPileType(order.getRequestMode())).add(order);
    }

    public int faultQueueSize(PileType type) {
        return faultQueue(type).size();
    }

    public boolean hasFaults(PileType type) {
        return !faultQueue(type).isEmpty();
    }

    public boolean hasAnyFault() {
        return !fastFaultQueue.isEmpty() || !slowFaultQueue.isEmpty();
    }

    public ChargingOrder pollFault(PileType type) {
        return faultQueue(type).poll();
    }

    // ---- 桩前队列 ----

    public int pileQueueSize(String pileId) {
        return pileQueue(pileId).size();
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

    /** 将即将充电的订单设为 position 0（跳过已在 position 0 的订单）。 */
    public void setCharging(String pileId, ChargingOrder order) {
        Deque<ChargingOrder> pq = pileQueue(pileId);
        if (pq.peekFirst() == order) return;
        pq.addFirst(order);
    }

    // ---- 事件处理 ----

    /**
     * 充电桩完成一轮充电时触发：
     * 1. position 0 出队（已完成的订单）
     * 2. 原 position 1 顶上 → 新的 position 0
     * 3. 空出一个位 → 等候区填补
     */
    public Optional<ChargingOrder> onPileReleased(String pileId, PileType pileType) {
        Deque<ChargingOrder> pq = pileQueue(pileId);
        pq.pollFirst();
        fillSlot(pileType, pq);
        return Optional.ofNullable(pq.peekFirst());
    }

    /** 充电桩故障：将排队车辆移入故障队列，充电车由 fault() 的 enqueueFault 管理。 */
    public List<ChargingOrder> onPileFaulted(String pileId, PileType pileType) {
        Deque<ChargingOrder> pq = pileQueue(pileId);
        Queue<ChargingOrder> fq = faultQueue(pileType);
        List<ChargingOrder> moved = new ArrayList<>();
        pq.pollFirst();
        while (!pq.isEmpty()) {
            moved.add(pq.pollFirst());
        }
        fq.addAll(moved);
        return moved;
    }

    /** 从桩前队列取 position 0 的订单并移除（用于故障恢复后启动充电）。 */
    public ChargingOrder pollFromPileQueue(String pileId) {
        Deque<ChargingOrder> pq = pileQueue(pileId);
        return pq.pollFirst();
    }
    /** 从等候区移除指定车辆的订单。 */
    public boolean removeFromWait(String carId) {
        return fastWaitQueue.removeIf(o -> carId.equals(o.getCarId()))
                || slowWaitQueue.removeIf(o -> carId.equals(o.getCarId()));
    }

    public java.util.Queue<ChargingOrder> getFastWaitQueue() { return fastWaitQueue; }
    public java.util.Queue<ChargingOrder> getSlowWaitQueue() { return slowWaitQueue; }
    /** 设置是否抑制 fillSlot 从等候区分发（边界步进有故障时暂缓）。 */
    public void setDeferWaitDispatch(boolean v) { this.deferWaitDispatch = v; }

    public void removeFromFaultQueues(String carId) {
        fastFaultQueue.removeIf(o -> carId.equals(o.getCarId()));
        slowFaultQueue.removeIf(o -> carId.equals(o.getCarId()));
    }

    /** 从所有桩前队列中移除指定车辆的订单。 */
    public void removeFromAllPileQueues(String carId) {
        for (var pq : pileQueues.values()) {
            pq.removeIf(o -> carId.equals(o.getCarId()));
        }
    }
    @EventListener
    public void onPileReleasedEvent(PileReleasedEvent event) {
        onPileReleased(event.pileId(), event.pileType());
    }
    /** 从所有桩前队列中移除指定车辆的订单。 */
    @EventListener
    public void onPileRecoveredEvent(PileRecoveredEvent event) {
        onPileReleased(event.pileId(), event.pileType());
    }

    // ---- 内部 ----

    /** 从等候区调一辆车填补桩队列空位（故障期间等候区冻结）。 */
    private void fillSlot(PileType pileType, Deque<ChargingOrder> pq) {
        if (pq.size() >= maxPileQueue) return;
        // 故障期间等候区停止调度
        if (!fastFaultQueue.isEmpty() || !slowFaultQueue.isEmpty()) return;
        if (deferWaitDispatch) return;
        Queue<ChargingOrder> wq = waitQueue(pileType);
        if (wq.isEmpty()) return;
        ChargingOrder next = scheduleStrategy.selectOne(wq.stream().toList());
        wq.remove(next);
        pq.addLast(next);
    }

    private static PileType toPileType(RequestMode mode) {
        return mode == RequestMode.FAST ? PileType.FAST : PileType.SLOW;
    }

    private Queue<ChargingOrder> faultQueue(PileType type) {
        return type == PileType.FAST ? fastFaultQueue : slowFaultQueue;
    }

    private Queue<ChargingOrder> waitQueue(PileType type) {
        return type == PileType.FAST ? fastWaitQueue : slowWaitQueue;
    }

    private Deque<ChargingOrder> pileQueue(String pileId) {
        return pileQueues.computeIfAbsent(pileId, k -> new LinkedList<>());
    }
}

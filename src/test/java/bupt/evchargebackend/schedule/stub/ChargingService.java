package bupt.evchargebackend.schedule.stub;

import bupt.evchargebackend.common.time.TimeProvider;
import bupt.evchargebackend.entity.bill.Bill;
import bupt.evchargebackend.entity.bill.enums.PaymentStatus;
import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.charging.ChargingSession;
import bupt.evchargebackend.entity.charging.enums.OrderStatus;
import bupt.evchargebackend.entity.charging.enums.RequestMode;
import bupt.evchargebackend.entity.charging.enums.SessionStatus;
import bupt.evchargebackend.entity.pile.ChargingPile;
import bupt.evchargebackend.entity.pile.enums.PileType;
import bupt.evchargebackend.entity.pile.enums.WorkingState;
import bupt.evchargebackend.service.schedule.SchedulingEngine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.UUID;

/**
 * 充电业务逻辑（Stub 版）：申请、结束、故障处理、调度联动。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
/**
 * 充电服务桩：管理订单提交、充电完成、故障/恢复、故障车重分配。
 *
 * <p>核心流程：
 * <ul>
 *   <li>submit — 无故障时直分配可用桩；有故障或所有桩队满则最优桩满→等候区</li>
 *   <li>finish — 占位结束 → onPileReleased 触发下一辆 + redistributeFaults</li>
 *   <li>fault — 先 enqueueFault（充电车）再 onPileFaulted（排队车），故障队列 [充, 排, ...]</li>
 *   <li>recover — 桩恢复 → onPileReleased + redistributeFaults + 等候区冲刷</li>
 *   <li>redistributeFaults — 按 totalActiveMinutes 选最短桩分发，working=1 限 1 辆/次</li>
 * </ul>
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
public class ChargingService {

    private final Stub stub;
    private final SchedulingEngine engine;
    private final TimeProvider timeProvider;

    public ChargingService(Stub stub, SchedulingEngine engine, TimeProvider timeProvider) {
        this.stub = stub;
        this.engine = engine;
        this.timeProvider = timeProvider;
    }

    /** 申请充电：有空桩直接分配，否则入等候区。 */
    public ChargingOrder submit(String carId, RequestMode mode, double amount) {
        ChargingOrder order = newOrder(carId, mode, amount);
        stub.insertOrder(order);
        PileType pileType = toPileType(mode);
        if (!engine.hasAnyFault()) {
            var available = stub.findAvailable(pileType);
            if (!available.isEmpty()) {
                startCharging(available.getFirst(), order);
                return order;
            }
        }
        var piles = stub.findPilesByType(pileType);
        piles.removeIf(p -> p.getWorkingState() == WorkingState.FAULT);
        piles.sort(Comparator.comparingLong(p -> totalActiveMinutes(p, pileType)));
        // 最优桩有空位？有 → 分配过去；无 → 等候区
        var best = piles.getFirst();
        if (engine.addToPileQueue(best.getPileId(), order)) {
            return order;
        }
        engine.enqueueWait(order);
        return order;
    }

    /** 结束充电 / 移出系统：处理充电中、队列中、等候区的车辆。 */
    public void finish(String carId) {
        ChargingSession session = findActiveSession(carId);
        if (session != null) {
            finishSession(session);
            ChargingPile pile = stub.getPile(session.getPileId());
            if (pile != null) {
                pile.setWorkingState(WorkingState.AVAILABLE);
                pile.setCurrentSessionId(null);
                engine.onPileReleased(pile.getPileId(), pile.getPileType())
                        .ifPresent(next -> startCharging(pile, next));
                redistributeFaults(pile.getPileType());
            }
            return;
        }
        if (engine.removeFromWait(carId)) return;
        engine.removeFromAllPileQueues(carId);
        engine.removeFromFaultQueues(carId);
    }

    /** 充电桩故障：中断当前充电，将排队车辆移入故障队列。 */
    public void fault(String pileId) {
        ChargingPile pile = stub.getPile(pileId);
        if (pile == null) return;
        pile.setWorkingState(WorkingState.FAULT);
        // 先入故障队列充电车，后入排队车 → 故障队列 [充, 排, ...]
        ChargingSession session = stub.findSessionByPile(pileId);
        if (session != null) {
            session.setSessionStatus(SessionStatus.INTERRUPTED);
            stub.updateSession(session);
            ChargingOrder order = stub.getOrder(session.getOrderId());
            if (order != null) {
                engine.enqueueFault(order);
            }
        }
        engine.onPileFaulted(pileId, pile.getPileType());
    }

    /** 故障恢复：桩可用 → 尝试调度下一辆。 */
    public void recover(String pileId) {
        ChargingPile pile = stub.getPile(pileId);
        if (pile == null) return;
        pile.setWorkingState(WorkingState.AVAILABLE);
        pile.setCurrentSessionId(null);
        engine.onPileReleased(pile.getPileId(), pile.getPileType())
                .ifPresent(next -> startCharging(pile, next));
        redistributeFaults(pile.getPileType());
        if (stub.findSessionByPile(pileId) == null
                && engine.pileQueueSize(pile.getPileId()) > 0) {
            var nextOrder = engine.pollFromPileQueue(pile.getPileId());
            if (nextOrder != null) startCharging(pile, nextOrder);
        }
        if (!engine.hasAnyFault()) {
            PileType[] pts = {PileType.FAST, PileType.SLOW};
            for (var pt : pts) {
                for (var p : stub.findPilesByType(pt)) {
                    if (p.getWorkingState() != WorkingState.AVAILABLE) continue;
                    var nextW = engine.pollWait(pt);
                    if (nextW != null) startCharging(p, nextW);
                }
            }
        }
    }

    /** 修改等候区车辆的充电量。 */
    public void changeAmount(String carId, double newAmount) {
        stub.orders.values().stream()
                .filter(o -> carId.equals(o.getCarId()))
                .filter(o -> o.getOrderStatus() == OrderStatus.WAITING)
                .findFirst()
                .ifPresent(o -> o.setTargetKwh(BigDecimal.valueOf(newAmount)));
    }

    /** 计算指定桩的总剩余充电时长（当前车辆 + 排队车辆，分钟）。 */
    private long totalActiveMinutes(ChargingPile pile, PileType pileType) {
        BigDecimal power = pileType == PileType.FAST ? new BigDecimal("30") : new BigDecimal("10");
        long total = 0;
        ChargingSession session = stub.findSessionByPile(pile.getPileId());
        if (session != null) {
            BigDecimal remaining = session.getTargetKwh().subtract(session.getChargedKwh());
            total += remaining.divide(power, 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(60)).longValue();
        }
        for (var order : engine.getPileQueue(pile.getPileId())) {
            total += order.getTargetKwh().divide(power, 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(60)).longValue();
        }
        return total;
    }

    /** 开始充电：创建充电过程、设置桩状态、更新引擎队列。 */
    private void startCharging(ChargingPile pile, ChargingOrder order) {
        pile.setWorkingState(WorkingState.CHARGING);
        ChargingSession session = new ChargingSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setOrderId(order.getOrderId());
        session.setCarId(order.getCarId());
        session.setPileId(pile.getPileId());
        session.setTargetKwh(order.getTargetKwh());
        session.setChargedKwh(BigDecimal.ZERO);
        session.setSessionStatus(SessionStatus.CHARGING);
        session.setStartTime(timeProvider.now());
        pile.setCurrentSessionId(session.getSessionId());
        stub.insertSession(session);
        order.setOrderStatus(OrderStatus.CHARGING);
        engine.setCharging(pile.getPileId(), order);
    }

    /** 结束充电过程并生成账单。 */
    private void finishSession(ChargingSession session) {
        session.setEndTime(timeProvider.now());
        session.setSessionStatus(SessionStatus.FINISHED);
        stub.updateSession(session);
        Bill bill = new Bill();
        bill.setBillId(UUID.randomUUID().toString());
        bill.setSessionId(session.getSessionId());
        bill.setChargedKwh(session.getChargedKwh());
        bill.setTotalFee(BigDecimal.ZERO);
        bill.setPaymentStatus(PaymentStatus.UNPAID);
        stub.insertBill(bill);
    }

    /** 按车辆 ID 查找正在充电的过程。 */
    private ChargingSession findActiveSession(String carId) {
        return stub.sessions.values().stream()
                .filter(s -> carId.equals(s.getCarId()))
                .filter(s -> s.getSessionStatus() == SessionStatus.CHARGING)
                .findFirst().orElse(null);
    }

    /** 将故障队列中的车分发到总充电时长最短的同类型桩。 */
    private void redistributeFaults(PileType pileType) {
        if (!engine.hasFaults(pileType)) return;
        var piles = stub.findPilesByType(pileType);
        piles.removeIf(p -> p.getWorkingState() == WorkingState.FAULT);
        if (piles.isEmpty()) return;
        long working = piles.size();
        int maxCars = working >= 2 ? Integer.MAX_VALUE : 1;
        int count = 0;
        while (engine.hasFaults(pileType) && count < maxCars) {
            piles.sort(Comparator.comparingLong(p -> totalActiveMinutes(p, pileType)));
            var target = piles.getFirst();
            if (engine.pileQueueSize(target.getPileId()) >= 3) break;
            ChargingOrder faultCar = engine.pollFault(pileType);
            if (faultCar == null) break;
            engine.removeFromWait(faultCar.getCarId());
            engine.addToPileQueue(target.getPileId(), faultCar);
            count++;
        }
    }

    /** 创建新的充电订单。 */
    private static ChargingOrder newOrder(String carId, RequestMode mode, double amount) {
        ChargingOrder order = new ChargingOrder();
        order.setOrderId(UUID.randomUUID().toString());
        order.setOrderNo("ORD-" + System.currentTimeMillis());
        order.setCarId(carId);
        order.setRequestMode(mode);
        order.setTargetKwh(BigDecimal.valueOf(amount));
        order.setOrderStatus(OrderStatus.WAITING);
        return order;
    }

    /** 充电模式 → 桩类型映射。 */
    private static PileType toPileType(RequestMode mode) {
        return mode == RequestMode.FAST ? PileType.FAST : PileType.SLOW;
    }
}

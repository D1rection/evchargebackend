package bupt.evchargebackend.stub;

import bupt.evchargebackend.entity.bill.Bill;
import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.charging.ChargingSession;
import bupt.evchargebackend.entity.pile.ChargingPile;
import bupt.evchargebackend.entity.pile.enums.PileType;
import bupt.evchargebackend.entity.pile.enums.WorkingState;
import bupt.evchargebackend.entity.pricing.BillingRatePeriod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存数据层，代替 Mapper。
 * <p>
 * Driver 验收测试使用，生产环境切换为真实 MyBatis-Plus Mapper。
 * </p>
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
public class Stub {

    public final Map<String, ChargingPile> piles = new ConcurrentHashMap<>();
    public final Map<String, ChargingOrder> orders = new ConcurrentHashMap<>();
    public final Map<String, ChargingSession> sessions = new ConcurrentHashMap<>();
    public final Map<String, Bill> bills = new ConcurrentHashMap<>();
    public final List<BillingRatePeriod> ratePeriods = new ArrayList<>();

    // ---- piles ----

    /** 初始化充电桩。 */
    public void initPile(ChargingPile pile) {
        piles.put(pile.getPileId(), pile);
    }

    /** 按 ID 获取充电桩。 */
    public ChargingPile getPile(String pileId) {
        return piles.get(pileId);
    }

    /** 查找指定类型的空闲充电桩。 */
    public List<ChargingPile> findAvailable(PileType type) {
        return piles.values().stream()
                .filter(p -> p.getPileType() == type)
                .filter(p -> p.getWorkingState() == WorkingState.AVAILABLE)
                .collect(Collectors.toList());
    }

    /** 查找指定类型的所有充电桩。 */
    public List<ChargingPile> findPilesByType(PileType type) {
        return piles.values().stream()
                .filter(p -> p.getPileType() == type)
                .collect(Collectors.toList());
    }

    // ---- orders ----

    /** 插入充电订单。 */
    public void insertOrder(ChargingOrder order) {
        orders.put(order.getOrderId(), order);
    }

    /** 按 ID 获取充电订单。 */
    public ChargingOrder getOrder(String orderId) {
        return orders.get(orderId);
    }

    // ---- sessions ----

    /** 插入充电过程。 */
    public void insertSession(ChargingSession session) {
        sessions.put(session.getSessionId(), session);
    }

    /** 更新充电过程。 */
    public void updateSession(ChargingSession session) {
        sessions.put(session.getSessionId(), session);
    }

    /** 按 ID 获取充电过程。 */
    public ChargingSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /** 查找指定充电桩上正在充电的过程。 */
    public ChargingSession findSessionByPile(String pileId) {
        return sessions.values().stream()
                .filter(s -> pileId.equals(s.getPileId()))
                .filter(s -> s.getSessionStatus() == bupt.evchargebackend.entity.charging.enums.SessionStatus.CHARGING)
                .findFirst().orElse(null);
    }

    /** 查找指定充电桩上最近一次充电过程（含已中断）。 */
    public ChargingSession findLastSessionByPile(String pileId) {
        return sessions.values().stream()
                .filter(s -> pileId.equals(s.getPileId()))
                .filter(s -> s.getSessionStatus() != bupt.evchargebackend.entity.charging.enums.SessionStatus.FINISHED)
                .findFirst().orElse(null);
    }

    // ---- bills ----

    /** 插入账单。 */
    public void insertBill(Bill bill) {
        bills.put(bill.getBillId(), bill);
    }
}

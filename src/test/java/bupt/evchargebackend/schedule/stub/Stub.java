package bupt.evchargebackend.schedule.stub;

import bupt.evchargebackend.entity.bill.Bill;
import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.charging.ChargingSession;
import bupt.evchargebackend.entity.pile.ChargingPile;
import bupt.evchargebackend.entity.pile.enums.PileType;
import bupt.evchargebackend.entity.pile.enums.WorkingState;
import bupt.evchargebackend.entity.pricing.BillingRatePeriod;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Stub {

    public final Map<String, ChargingPile> piles = new ConcurrentHashMap<>();
    public final Map<String, ChargingOrder> orders = new ConcurrentHashMap<>();
    public final Map<String, ChargingSession> sessions = new ConcurrentHashMap<>();
    public final Map<String, Bill> bills = new ConcurrentHashMap<>();
    public final Map<String, BigDecimal> sessionFees = new ConcurrentHashMap<>();
    public final List<BillingRatePeriod> ratePeriods = new ArrayList<>();

    public void initPile(ChargingPile pile) {
        piles.put(pile.getPileId(), pile);
    }

    public ChargingPile getPile(String pileId) {
        return piles.get(pileId);
    }

    public List<ChargingPile> findAvailable(PileType type) {
        return piles.values().stream()
                .filter(p -> p.getPileType() == type)
                .filter(p -> p.getWorkingState() == WorkingState.AVAILABLE)
                .collect(Collectors.toList());
    }

    public List<ChargingPile> findPilesByType(PileType type) {
        return piles.values().stream()
                .filter(p -> p.getPileType() == type)
                .collect(Collectors.toList());
    }

    public void insertOrder(ChargingOrder order) {
        orders.put(order.getOrderId(), order);
    }

    public ChargingOrder getOrder(String orderId) {
        return orders.get(orderId);
    }

    public void insertSession(ChargingSession session) {
        sessions.put(session.getSessionId(), session);
    }

    public void updateSession(ChargingSession session) {
        sessions.put(session.getSessionId(), session);
    }

    public ChargingSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public BigDecimal getSessionFee(String sessionId) {
        return sessionFees.getOrDefault(sessionId, BigDecimal.ZERO);
    }

    public void addSessionFee(String sessionId, BigDecimal fee) {
        sessionFees.merge(sessionId, fee, BigDecimal::add);
    }

    public void removeSessionFee(String sessionId) {
        sessionFees.remove(sessionId);
    }

    public ChargingSession findSessionByPile(String pileId) {
        return sessions.values().stream()
                .filter(s -> pileId.equals(s.getPileId()))
                .filter(s -> s.getSessionStatus() == bupt.evchargebackend.entity.charging.enums.SessionStatus.CHARGING)
                .findFirst().orElse(null);
    }

    public ChargingSession findLastSessionByPile(String pileId) {
        return sessions.values().stream()
                .filter(s -> pileId.equals(s.getPileId()))
                .filter(s -> s.getSessionStatus() != bupt.evchargebackend.entity.charging.enums.SessionStatus.FINISHED)
                .findFirst().orElse(null);
    }

    public void insertBill(Bill bill) {
        bills.put(bill.getBillId(), bill);
    }
}

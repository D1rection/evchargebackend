package bupt.evchargebackend.service.schedule;

import bupt.evchargebackend.entity.charging.ChargingOrder;

import java.util.ArrayList;
import java.util.List;

/**
 * 调度策略：从等候区／排队队列的候选中选出待分配车辆。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
public interface ScheduleStrategy {

    /**
     * 单次调度：释放一个桩位时，从候选中选出一辆。
     *
     * @param candidates 当前同类型等待队列中的订单，按到达先后排序
     * @return 被选中的订单，调用方保证 candidates 非空
     */
    ChargingOrder selectOne(List<ChargingOrder> candidates);

    /**
     * 批量调度：多个桩位同时释放时，从候选中选出多辆。
     *
     * <p>默认实现逐次调用 {@link #selectOne}，子类可按需重写（如全局最优匹配）。
     *
     * @param candidates 当前同类型等待队列中的订单，按到达先后排序
     * @param slotCount  当前可用的桩位数量
     * @return 被选中的订单列表
     */
    default List<ChargingOrder> selectBatch(List<ChargingOrder> candidates, int slotCount) {
        List<ChargingOrder> result = new ArrayList<>(slotCount);
        List<ChargingOrder> remaining = new ArrayList<>(candidates);
        for (int i = 0; i < slotCount && !remaining.isEmpty(); i++) {
            ChargingOrder selected = selectOne(remaining);
            result.add(selected);
            remaining.remove(selected);
        }
        return result;
    }
}

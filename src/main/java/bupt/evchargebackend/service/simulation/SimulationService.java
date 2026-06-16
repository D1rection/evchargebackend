package bupt.evchargebackend.service.simulation;

import bupt.evchargebackend.common.response.Result;

import java.time.LocalDateTime;
import java.util.Map;

public interface SimulationService {

    /** 开启模拟，设置起始时间。 */
    Result<Void> startSimulation(LocalDateTime time);

    /** 步进指定分钟数，自动触发事件并推进充电。 */
    Result<Void> step(int minutes);

    /** 停止模拟，切回真实时间。 */
    Result<Void> stopSimulation();

    /** 获取当前全量快照。 */
    Result<Map<String, Object>> getState();
}

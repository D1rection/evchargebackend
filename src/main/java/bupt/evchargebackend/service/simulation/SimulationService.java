package bupt.evchargebackend.service.simulation;

import bupt.evchargebackend.common.response.Result;

import java.time.LocalDateTime;
import java.util.List;
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

    /**
     * 自动播放：重置数据库 → 初始化基础数据 → 自动步进至模拟结束。
     *
     * @param speed normal=1:1实时比例, fast=10倍速, instant=立即完成
     */
    Result<Void> play(String speed);

    /** 暂停自动播放。 */
    Result<Void> pause();

    /** 返回自动播放期间所有 checkpoints 快照列表（播放结束后调用）。 */
    Result<List<Map<String, Object>>> getCheckpoints();

    /** 是否正在自动播放。 */
    Result<Boolean> isPlaying();
}

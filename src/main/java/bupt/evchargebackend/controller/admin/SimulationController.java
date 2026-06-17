package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.service.simulation.SimulationService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 模拟时间推进控制器，用于演示验收用例。
 *
 * @author Deng Chao
 * @since 2026-06-17
 */
@RestController
@RequestMapping("/admin/simulation")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    /**
     * 开启模拟，设定起始时间。
     *
     * @param time 起始时间 ISO 格式，如 "2026-06-17T06:00"
     */
    @PostMapping("/start")
    public Result<Void> start(@RequestParam String time) {
        return simulationService.startSimulation(LocalDateTime.parse(time));
    }

    /**
     * 步进指定分钟数，自动触发到时间的事件并推进充电。
     *
     * @param minutes 步进分钟数，默认 5
     */
    @PostMapping("/step")
    public Result<Void> step(@RequestParam(defaultValue = "5") int minutes) {
        return simulationService.step(minutes);
    }

    /**
     * 停止模拟，切回真实时间。
     */
    @PostMapping("/stop")
    public Result<Void> stop() {
        return simulationService.stopSimulation();
    }

    /**
     * 获取当前全量快照（桩状态、等候区、故障区、已完成订单）。
     */
    @GetMapping("/state")
    public Result<Map<String, Object>> state() {
        return simulationService.getState();
    }

    /**
     * 自动播放：重置数据库 → 初始化数据 → 自动步进至所有车辆充电完成。
     * <p>调用后用 {@code GET /state} 轮询进度，播放结束后用 {@code GET /checkpoints} 获取全量快照。</p>
     *
     * @param speed normal=1:1真实比例(30s/步), fast=10倍速(3s/步), instant=立即完成
     */
    @PostMapping("/play")
    public Result<Void> play(@RequestParam(defaultValue = "fast") String speed) {
        return simulationService.play(speed);
    }

    /**
     * 暂停自动播放。
     */
    @PostMapping("/pause")
    public Result<Void> pause() {
        return simulationService.pause();
    }

    /**
     * 获取自动播放期间所有 checkpoints 快照列表。
     */
    @GetMapping("/checkpoints")
    public Result<List<Map<String, Object>>> checkpoints() {
        return simulationService.getCheckpoints();
    }

    /**
     * 是否正在自动播放。
     */
    @GetMapping("/playing")
    public Result<Boolean> playing() {
        return simulationService.isPlaying();
    }
}

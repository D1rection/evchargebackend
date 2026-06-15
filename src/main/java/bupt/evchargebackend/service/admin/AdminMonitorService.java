package bupt.evchargebackend.service.admin;

import bupt.evchargebackend.common.response.PageResult;

import java.util.Map;

/**
 * 管理员监控统计服务。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
public interface AdminMonitorService {

    /**
     * 获取充电桩状态列表，支持分页。
     * <p>
     * 不传分页参数时返回全部数据。
     *
     * @param pageNum  页码（从 1 开始），{@code null} 表示不分页
     * @param pageSize 每页条数，{@code null} 表示不分页
     * @return 分页包装的充电桩状态列表
     */
    PageResult<Map<String, Object>> listPileStatus(Integer pageNum, Integer pageSize);

    /**
     * 获取仪表盘概览数据。
     * <p>
     * 包含今日充电次数、今日收入、设备在线率、当前故障数等关键运营指标。
     *
     * @return 仪表盘数据 Map
     */
    Map<String, Object> getDashboard();
}

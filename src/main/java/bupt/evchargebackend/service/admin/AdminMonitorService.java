package bupt.evchargebackend.service.admin;

import bupt.evchargebackend.common.response.PageResult;
import bupt.evchargebackend.common.response.Result;

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
     *
     * @param pageNum  页码，{@code null} 表示不分页
     * @param pageSize 每页条数，{@code null} 表示不分页
     * @return 分页包装的充电桩状态列表
     */
    Result<PageResult<Map<String, Object>>> listPileStatus(Integer pageNum, Integer pageSize);

    /**
     * 获取仪表盘概览数据。
     *
     * @return 仪表盘数据 Map
     */
    Result<Map<String, Object>> getDashboard();
}

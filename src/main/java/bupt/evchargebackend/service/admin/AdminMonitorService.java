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

    /** 获取充电桩状态列表（分页），不传分页参数则返回全部 */
    PageResult<Map<String, Object>> listPileStatus(Integer pageNum, Integer pageSize);

    /** 获取仪表盘概览数据 */
    Map<String, Object> getDashboard();
}

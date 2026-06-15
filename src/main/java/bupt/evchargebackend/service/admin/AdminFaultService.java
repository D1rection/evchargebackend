package bupt.evchargebackend.service.admin;

import bupt.evchargebackend.common.response.PageResult;
import bupt.evchargebackend.common.response.Result;

import java.util.Map;

/**
 * 管理员故障运维服务。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
public interface AdminFaultService {

    /**
     * 分页查询故障记录列表，支持按状态筛选。
     *
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @param status   故障状态筛选（1=待处置，2=已处置），{@code null} 表示全部
     * @return 分页包装的故障记录列表
     */
    Result<PageResult<Map<String, Object>>> listFaults(int pageNum, int pageSize, Integer status);

    /**
     * 标记故障为已处置。
     *
     * @param faultId     故障记录 ID
     * @param resolveCode 处置码
     * @param remark      处置备注
     * @return 成功时 {@code code=200}；不存在时 {@code code=404}；已处置时 {@code code=409}
     */
    Result<Void> resolveFault(String faultId, Integer resolveCode, String remark);
}

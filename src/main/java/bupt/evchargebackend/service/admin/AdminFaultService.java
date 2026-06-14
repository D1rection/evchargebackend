package bupt.evchargebackend.service.admin;

import bupt.evchargebackend.common.response.PageResult;

import java.util.Map;

/**
 * 管理员故障运维服务。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
public interface AdminFaultService {

    /** 分页查询故障列表，可按状态筛选 */
    PageResult<Map<String, Object>> listFaults(int pageNum, int pageSize, Integer status);

    /** 标记故障为已处置 */
    void resolveFault(String faultId, Integer resolveCode, String remark);
}

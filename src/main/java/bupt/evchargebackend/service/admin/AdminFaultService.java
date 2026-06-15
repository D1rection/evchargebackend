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

    /**
     * 分页查询故障记录列表，支持按状态筛选。
     *
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @param status   故障状态筛选（1=待处置，2=已处置），{@code null} 表示全部
     * @return 分页包装的故障记录列表
     */
    PageResult<Map<String, Object>> listFaults(int pageNum, int pageSize, Integer status);

    /**
     * 标记故障为已处置。
     *
     * @param faultId     故障记录 ID
     * @param resolveCode 处置码（200=复位，201=换硬件，202=换通信，203=重启，204=其他）
     * @param remark      处置备注
     * @throws bupt.evchargebackend.common.exception.BusinessException 故障不存在或已处置时抛出
     */
    void resolveFault(String faultId, Integer resolveCode, String remark);
}

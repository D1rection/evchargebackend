package bupt.evchargebackend.service.admin.impl;

import bupt.evchargebackend.common.response.PageResult;
import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.service.admin.AdminFaultService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 管理员故障运维服务实现。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@Service
public class AdminFaultServiceImpl implements AdminFaultService {

    private final JdbcTemplate jdbcTemplate;

    public AdminFaultServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Result<PageResult<Map<String, Object>>> listFaults(int pageNum, int pageSize, Integer status) {
        StringBuilder whereSql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (status != null) {
            whereSql.append(" WHERE fault_status = ?");
            params.add(status == 1 ? "ACTIVE" : "RECOVERED");
        }

        String countSql = "SELECT COUNT(*) FROM fault_record" + whereSql;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());

        int offset = (pageNum - 1) * pageSize;
        String pageSql = "SELECT fault_id, pile_id, fault_code, fault_time, fault_status, " +
                         "resolve_code, recover_time, resolver, remark " +
                         "FROM fault_record" + whereSql +
                         " ORDER BY fault_time DESC LIMIT ? OFFSET ?";
        params.add(pageSize);
        params.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(pageSql, params.toArray());
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("fault_id"));
            item.put("pileId", row.get("pile_id"));
            item.put("faultCode", row.get("fault_code"));
            item.put("faultTime", row.get("fault_time") != null ? row.get("fault_time").toString() : null);
            String fs = (String) row.get("fault_status");
            item.put("status", "ACTIVE".equals(fs) ? 1 : 2);
            item.put("resolveCode", row.get("resolve_code"));
            item.put("resolveTime", row.get("recover_time") != null ? row.get("recover_time").toString() : "");
            item.put("resolver", row.get("resolver") != null ? row.get("resolver").toString() : "");
            item.put("remark", row.get("remark") != null ? row.get("remark").toString() : "");
            list.add(item);
        }

        return Result.success(PageResult.of(list, total != null ? total : 0, pageNum, pageSize));
    }

    @Override
    public Result<Void> resolveFault(String faultId, Integer resolveCode, String remark) {
        List<String> results = jdbcTemplate.queryForList(
                "SELECT fault_status FROM fault_record WHERE fault_id = ?", String.class, faultId);
        if (results.isEmpty()) {
            return Result.error(404, "故障记录不存在");
        }
        if ("RECOVERED".equals(results.get(0))) {
            return Result.error(409, "故障已处置");
        }

        jdbcTemplate.update(
                "UPDATE fault_record SET fault_status = 'RECOVERED', " +
                "resolve_code = ?, remark = ?, recover_time = NOW() WHERE fault_id = ?",
                resolveCode, remark, faultId);
        return Result.success();
    }
}

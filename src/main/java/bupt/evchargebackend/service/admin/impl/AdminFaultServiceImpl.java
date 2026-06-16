package bupt.evchargebackend.service.admin.impl;

import bupt.evchargebackend.common.exception.BusinessException;
import bupt.evchargebackend.common.exception.ErrorCode;
import bupt.evchargebackend.common.response.PageResult;
import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.service.admin.AdminFaultService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AdminFaultServiceImpl implements AdminFaultService {

    private final JdbcTemplate jdbcTemplate;

    public AdminFaultServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Result<PageResult<Map<String, Object>>> listFaults(int pageNum, int pageSize, Integer status) {
        if (pageNum < 1) pageNum = 1;
        if (pageSize < 1) pageSize = 10;

        StringBuilder whereSql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (status != null) {
            whereSql.append(" WHERE fr.fault_status = ?");
            params.add(status == 1 ? "ACTIVE" : "RECOVERED");
        }

        String countSql = "SELECT COUNT(*) FROM fault_record fr" + whereSql;
        Long total = jdbcTemplate.query(countSql,
                rs -> rs.next() ? rs.getLong(1) : 0L,
                params.toArray());

        int offset = (pageNum - 1) * pageSize;
        String pageSql = "SELECT fr.fault_id, fr.pile_id, cp.pile_no AS pileNo, fr.fault_code, fr.fault_time, fr.fault_status, " +
                         "fr.resolve_code, fr.recover_time, fr.resolver, fr.remark " +
                         "FROM fault_record fr LEFT JOIN charging_pile cp ON fr.pile_id = cp.pile_id" + whereSql +
                         " ORDER BY fr.fault_time DESC LIMIT ? OFFSET ?";
        params.add(pageSize);
        params.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(pageSql, params.toArray());
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("fault_id"));
            item.put("pileId", row.get("pile_id"));
            item.put("pileNo", row.get("pileNo"));
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
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if ("RECOVERED".equals(results.get(0))) {
            throw new BusinessException(ErrorCode.FAULT_ALREADY_RESOLVED);
        }

        jdbcTemplate.update(
                "UPDATE fault_record SET fault_status = 'RECOVERED', " +
                "resolve_code = ?, remark = ?, recover_time = NOW() WHERE fault_id = ?",
                resolveCode, remark, faultId);
        return Result.success();
    }
}

package bupt.evchargebackend.service.admin.impl;

import bupt.evchargebackend.common.exception.BusinessException;
import bupt.evchargebackend.common.exception.ErrorCode;
import bupt.evchargebackend.common.response.PageResult;
import bupt.evchargebackend.entity.fault.FaultRecord;
import bupt.evchargebackend.entity.fault.enums.FaultStatus;
import bupt.evchargebackend.mapper.fault.FaultRecordMapper;
import bupt.evchargebackend.service.admin.AdminFaultService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 管理员故障运维服务实现。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@Service
public class AdminFaultServiceImpl implements AdminFaultService {

    private final FaultRecordMapper faultRecordMapper;

    public AdminFaultServiceImpl(FaultRecordMapper faultRecordMapper) {
        this.faultRecordMapper = faultRecordMapper;
    }

    @Override
    public PageResult<Map<String, Object>> listFaults(int pageNum, int pageSize, Integer status) {
        LambdaQueryWrapper<FaultRecord> wrapper = new LambdaQueryWrapper<>();

        // 状态筛选：1=待处置(ACTIVE), 2=已处置(RECOVERED)
        if (status != null) {
            if (status == 1) {
                wrapper.eq(FaultRecord::getFaultStatus, FaultStatus.ACTIVE);
            } else if (status == 2) {
                wrapper.eq(FaultRecord::getFaultStatus, FaultStatus.RECOVERED);
            }
        }

        wrapper.orderByDesc(FaultRecord::getFaultTime);

        Page<FaultRecord> page = new Page<>(pageNum, pageSize);
        Page<FaultRecord> result = faultRecordMapper.selectPage(page, wrapper);

        List<Map<String, Object>> list = new ArrayList<>();
        for (FaultRecord f : result.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", f.getFaultId());
            item.put("pileId", f.getPileId());
            item.put("faultCode", f.getFaultCode());
            item.put("faultTime", f.getFaultTime() != null ? f.getFaultTime().toString() : null);
            item.put("status", f.getFaultStatus() == FaultStatus.ACTIVE ? 1 : 2);
            item.put("resolveCode", f.getResolveCode());
            item.put("resolveTime", f.getRecoverTime() != null ? f.getRecoverTime().toString() : "");
            item.put("resolver", f.getResolver() != null ? f.getResolver() : "");
            item.put("remark", f.getRemark() != null ? f.getRemark() : "");
            list.add(item);
        }

        return PageResult.of(list, result.getTotal(), pageNum, pageSize);
    }

    @Override
    public void resolveFault(String faultId, Integer resolveCode, String remark) {
        FaultRecord fault = faultRecordMapper.selectById(faultId);
        if (fault == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND.getCode(), "故障记录不存在");
        }
        if (fault.getFaultStatus() == FaultStatus.RECOVERED) {
            throw new BusinessException(ErrorCode.FAULT_ALREADY_RESOLVED);
        }

        fault.setFaultStatus(FaultStatus.RECOVERED);
        fault.setResolveCode(resolveCode);
        fault.setRemark(remark);
        fault.setRecoverTime(LocalDateTime.now());
        faultRecordMapper.updateById(fault);
    }
}

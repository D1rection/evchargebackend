package bupt.evchargebackend.mapper.fault;

import bupt.evchargebackend.entity.fault.FaultRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FaultRecordMapper extends BaseMapper<FaultRecord> {
}

package bupt.evchargebackend.mapper.charging;

import bupt.evchargebackend.entity.charging.ChargingOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChargingOrderMapper extends BaseMapper<ChargingOrder> {
}

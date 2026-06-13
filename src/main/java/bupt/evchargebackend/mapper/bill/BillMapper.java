package bupt.evchargebackend.mapper.bill;

import bupt.evchargebackend.entity.bill.Bill;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BillMapper extends BaseMapper<Bill> {
}

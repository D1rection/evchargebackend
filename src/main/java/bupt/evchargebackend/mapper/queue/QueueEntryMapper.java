package bupt.evchargebackend.mapper.queue;

import bupt.evchargebackend.entity.queue.QueueEntry;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QueueEntryMapper extends BaseMapper<QueueEntry> {
}

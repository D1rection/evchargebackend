package bupt.evchargebackend.entity.queue;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 队列持久化条目：统一存储等候区、故障队列、桩队列。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@Data
@TableName("queue_entry")
public class QueueEntry {

    private Long id;
    private String queueType;
    private String queueKey;
    private String orderId;
    private LocalDateTime createdAt;
}

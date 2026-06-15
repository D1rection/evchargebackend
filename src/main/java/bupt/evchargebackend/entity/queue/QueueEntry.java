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
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getQueueType() {
        return queueType;
    }

    public void setQueueType(String queueType) {
        this.queueType = queueType;
    }

    public String getQueueKey() {
        return queueKey;
    }

    public void setQueueKey(String queueKey) {
        this.queueKey = queueKey;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

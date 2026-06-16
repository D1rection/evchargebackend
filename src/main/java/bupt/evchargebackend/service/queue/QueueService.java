package bupt.evchargebackend.service.queue;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.queue.WaitingQueueItem;

import java.util.List;

public interface QueueService {

    /**
     * 查看等候区队列。
     *
     * @param queueMode fast / slow / all
     * @return 排队车辆列表
     */
    Result<List<WaitingQueueItem>> getWaitingQueue(String queueMode);
}

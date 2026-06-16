package bupt.evchargebackend.controller.admin;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.queue.WaitingQueueItem;
import bupt.evchargebackend.service.queue.QueueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 队列管理（等候区）。
 *
 * @author Deng Chao
 * @since 2026-06-16
 */
@RestController
@RequestMapping("/admin/queues")
public class AdminQueueController {

    private final QueueService queueService;

    public AdminQueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping("/state")
    public Result<List<WaitingQueueItem>> getState(@RequestParam String queueMode) {
        return queueService.getWaitingQueue(queueMode);
    }
}

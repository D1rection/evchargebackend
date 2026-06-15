package bupt.evchargebackend.controller.pile;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.pile.PileOperationRequest;
import bupt.evchargebackend.dto.pile.PileQueueItem;
import bupt.evchargebackend.entity.pile.ChargingPile;
import bupt.evchargebackend.service.pile.PileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/piles")
public class PileController {

    private final PileService pileService;

    public PileController(PileService pileService) {
        this.pileService = pileService;
    }

    @GetMapping("/state")
    public Result<List<ChargingPile>> listPileStates(@RequestParam(required = false) String pileId) {
        return Result.success(pileService.listPileStates(pileId));
    }

    @PostMapping("/power-on")
    public Result<ChargingPile> powerOn(@Valid @RequestBody PileOperationRequest request) {
        return Result.success(pileService.powerOn(request.getPileId()));
    }

    @PostMapping("/power-off")
    public Result<ChargingPile> powerOff(@Valid @RequestBody PileOperationRequest request) {
        return Result.success(pileService.powerOff(request.getPileId()));
    }

    @PostMapping("/start")
    public Result<ChargingPile> start(@Valid @RequestBody PileOperationRequest request) {
        return Result.success(pileService.start(request.getPileId()));
    }

    /**
     * 查看充电桩前排队：返回当前充电车辆 + 等待车辆列表。
     */
    @GetMapping("/{pileId}/queue")
    public Result<List<PileQueueItem>> getPileQueue(@PathVariable String pileId) {
        return pileService.getPileQueue(pileId);
    }
}

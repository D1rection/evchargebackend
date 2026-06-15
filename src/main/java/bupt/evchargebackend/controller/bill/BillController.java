package bupt.evchargebackend.controller.bill;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.bill.BillPayRequest;
import bupt.evchargebackend.entity.bill.Bill;
import bupt.evchargebackend.service.bill.BillService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bill")
public class BillController {

    private final BillService billService;

    public BillController(BillService billService) {
        this.billService = billService;
    }

    @GetMapping("/query")
    public Result<List<Bill>> queryBills(
            @RequestParam String carId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(billService.queryBills(carId, date));
    }

    @GetMapping("/detailed")
    public Result<Bill> getBillDetail(
            @RequestParam(required = false) String billId,
            @RequestParam(required = false) String billNo,
            @RequestParam(required = false) String orderId) {
        return Result.success(billService.getBillDetail(billId, billNo, orderId));
    }

    @PostMapping("/pay")
    public Result<Bill> payBill(@Valid @RequestBody BillPayRequest request) {
        return Result.success(billService.payBill(request.getBillId()));
    }
}

package bupt.evchargebackend.service.bill;

import bupt.evchargebackend.entity.bill.Bill;

import java.time.LocalDate;
import java.util.List;

public interface BillService {

    List<Bill> queryBills(String carId, LocalDate date);

    Bill getBillDetail(String billId, String billNo, String orderId);

    Bill payBill(String billId);
}

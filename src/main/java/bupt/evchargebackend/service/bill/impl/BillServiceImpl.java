package bupt.evchargebackend.service.bill.impl;

import bupt.evchargebackend.common.exception.BusinessException;
import bupt.evchargebackend.common.exception.ErrorCode;
import bupt.evchargebackend.entity.bill.Bill;
import bupt.evchargebackend.entity.bill.enums.PaymentStatus;
import bupt.evchargebackend.entity.pile.ChargingPile;
import bupt.evchargebackend.entity.user.Car;
import bupt.evchargebackend.mapper.bill.BillMapper;
import bupt.evchargebackend.mapper.pile.ChargingPileMapper;
import bupt.evchargebackend.mapper.user.CarMapper;
import bupt.evchargebackend.service.bill.BillService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BillServiceImpl implements BillService {

    private final BillMapper billMapper;
    private final ChargingPileMapper chargingPileMapper;
    private final CarMapper carMapper;

    public BillServiceImpl(BillMapper billMapper, ChargingPileMapper chargingPileMapper,
                           CarMapper carMapper) {
        this.billMapper = billMapper;
        this.chargingPileMapper = chargingPileMapper;
        this.carMapper = carMapper;
    }

    @Override
    public List<Bill> queryBills(String carId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        List<Bill> bills = billMapper.selectList(
                new QueryWrapper<Bill>()
                        .eq("car_id", carId)
                        .ge("created_at", start)
                        .lt("created_at", end)
                        .orderByDesc("created_at")
        );
        bills.forEach(this::enrichPileNo);
        bills.forEach(this::enrichCarNo);
        return bills;
    }

    @Override
    public Bill getBillDetail(String billId, String billNo, String orderId) {
        Bill bill;
        if (hasText(billId)) {
            bill = requireBillById(billId);
        } else {
            QueryWrapper<Bill> wrapper = new QueryWrapper<>();
            if (hasText(billNo)) {
                wrapper.eq("bill_no", billNo);
            } else if (hasText(orderId)) {
                wrapper.eq("order_id", orderId);
            } else {
                throw new BusinessException("billId, billNo or orderId is required");
            }
            bill = billMapper.selectOne(wrapper.last("LIMIT 1"));
            if (bill == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
        }
        enrichPileNo(bill);
        enrichCarNo(bill);
        return bill;
    }

    @Override
    public Bill payBill(String billId) {
        Bill bill = requireBillById(billId);
        if (bill.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BusinessException(409, "bill already paid");
        }
        bill.setPaymentStatus(PaymentStatus.PAID);
        billMapper.updateById(bill);
        enrichPileNo(bill);
        enrichCarNo(bill);
        return bill;
    }

    private void enrichPileNo(Bill bill) {
        if (bill.getPileId() != null && bill.getPileNo() == null) {
            ChargingPile pile = chargingPileMapper.selectById(bill.getPileId());
            if (pile != null) {
                bill.setPileNo(pile.getPileNo());
            }
        }
    }

    private void enrichCarNo(Bill bill) {
        if (bill.getCarId() != null && bill.getCarNo() == null) {
            Car car = carMapper.selectById(bill.getCarId());
            if (car != null) {
                bill.setCarNo(car.getCarNo());
            }
        }
    }

    private Bill requireBillById(String billId) {
        Bill bill = billMapper.selectById(billId);
        if (bill == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return bill;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

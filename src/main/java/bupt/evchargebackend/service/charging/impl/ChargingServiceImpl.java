package bupt.evchargebackend.service.charging.impl;

import bupt.evchargebackend.common.exception.BusinessException;
import bupt.evchargebackend.common.exception.ErrorCode;
import bupt.evchargebackend.entity.charging.ChargingOrder;
import bupt.evchargebackend.entity.charging.enums.RequestMode;
import bupt.evchargebackend.mapper.charging.ChargingOrderMapper;
import bupt.evchargebackend.service.charging.ChargingService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ChargingServiceImpl implements ChargingService {

    private final ChargingOrderMapper chargingOrderMapper;

    public ChargingServiceImpl(ChargingOrderMapper chargingOrderMapper) {
        this.chargingOrderMapper = chargingOrderMapper;
    }

    @Override
    public ChargingOrder modifyAmount(String carId, BigDecimal amount) {
        if (!hasText(carId)) {
            throw new BusinessException("carId is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("amount must be greater than 0");
        }

        ChargingOrder order = requireModifiableOrder(carId);
        order.setTargetKwh(amount);
        chargingOrderMapper.updateById(order);
        return order;
    }

    @Override
    public ChargingOrder modifyMode(String carId, RequestMode requestMode) {
        if (!hasText(carId)) {
            throw new BusinessException("carId is required");
        }
        if (requestMode == null) {
            throw new BusinessException("requestMode is required");
        }

        ChargingOrder order = requireModifiableOrder(carId);
        order.setRequestMode(requestMode);
        chargingOrderMapper.updateById(order);
        return order;
    }

    private ChargingOrder requireModifiableOrder(String carId) {
        ChargingOrder order = chargingOrderMapper.selectOne(
                new QueryWrapper<ChargingOrder>()
                        .eq("car_id", carId)
                        .in("order_status", List.of("WAITING", "CALLED"))
                        .orderByDesc("created_at")
                        .last("LIMIT 1")
        );
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return order;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

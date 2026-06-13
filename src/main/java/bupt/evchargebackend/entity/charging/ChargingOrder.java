package bupt.evchargebackend.entity.charging;

import bupt.evchargebackend.entity.charging.enums.OrderStatus;
import bupt.evchargebackend.entity.charging.enums.RequestMode;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充电订单表。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
@Data
@TableName("charging_order")
public class ChargingOrder {

    /** 主键 */
    @TableId
    private String orderId;

    /** 订单号，对外展示 */
    private String orderNo;

    /** 车辆ID，外键 */
    private String carId;

    /** 充电模式 */
    private RequestMode requestMode;

    /** 目标充电量（kWh） */
    private BigDecimal targetKwh;

    /** 预估费用（元） */
    private BigDecimal estimatedFee;

    /** 预估充电时长（分钟） */
    private Integer estimatedMinutes;

    /** 订单状态 */
    private OrderStatus orderStatus;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

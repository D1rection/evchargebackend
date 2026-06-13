package bupt.evchargebackend.entity.bill;

import bupt.evchargebackend.entity.bill.enums.PaymentStatus;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账单表。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
@Data
@TableName("bill")
public class Bill {

    /** 主键 */
    @TableId
    private String billId;

    /** 账单号，对外展示 */
    private String billNo;

    /** 订单ID，外键 */
    private String orderId;

    /** 充电会话ID，外键 */
    private String sessionId;

    /** 车辆ID，外键 */
    private String carId;

    /** 充电桩ID，外键 */
    private String pileId;

    /** 充电开始时间 */
    private LocalDateTime startTime;

    /** 充电结束时间 */
    private LocalDateTime endTime;

    /** 充电量（kWh） */
    private BigDecimal chargedKwh;

    /** 充电时长（分钟） */
    private Integer chargeMinutes;

    /** 电费（元） */
    private BigDecimal electricityFee;

    /** 服务费（元） */
    private BigDecimal serviceFee;

    /** 总费用（元） */
    private BigDecimal totalFee;

    /** 支付状态 */
    private PaymentStatus paymentStatus;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

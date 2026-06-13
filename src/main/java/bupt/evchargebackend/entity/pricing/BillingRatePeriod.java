package bupt.evchargebackend.entity.pricing;

import bupt.evchargebackend.entity.pile.enums.PileType;
import bupt.evchargebackend.entity.pricing.enums.PeriodName;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分时电价表。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
@Data
@TableName("billing_rate_period")
public class BillingRatePeriod {

    /** 主键 */
    @TableId
    private String periodId;

    /** 适用桩类型 */
    private PileType pileType;

    /** 时段名称 */
    private PeriodName periodName;

    /** 时段开始时间（HH:mm） */
    private String startTime;

    /** 时段结束时间（HH:mm） */
    private String endTime;

    /** 电价（元/kWh） */
    private BigDecimal electricityPrice;

    /** 服务费（元/kWh） */
    private BigDecimal servicePrice;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

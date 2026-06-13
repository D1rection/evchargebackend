package bupt.evchargebackend.entity.charging;

import bupt.evchargebackend.entity.charging.enums.SessionStatus;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充电过程表。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
@Data
@TableName("charging_session")
public class ChargingSession {

    /** 主键 */
    @TableId
    private String sessionId;

    /** 订单ID，外键 */
    private String orderId;

    /** 车辆ID，外键 */
    private String carId;

    /** 充电桩ID，外键 */
    private String pileId;

    /** 充电开始时间 */
    private LocalDateTime startTime;

    /** 充电结束时间 */
    private LocalDateTime endTime;

    /** 目标充电量（kWh） */
    private BigDecimal targetKwh;

    /** 已充电量（kWh） */
    private BigDecimal chargedKwh;

    /** 会话状态 */
    private SessionStatus sessionStatus;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

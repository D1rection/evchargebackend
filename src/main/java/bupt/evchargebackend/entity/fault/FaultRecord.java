package bupt.evchargebackend.entity.fault;

import bupt.evchargebackend.entity.fault.enums.FaultStatus;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 故障记录表。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
@Data
@TableName("fault_record")
public class FaultRecord {

    /** 主键 */
    @TableId
    private String faultId;

    /** 故障桩ID，外键 */
    private String pileId;

    /** 关联充电会话ID */
    private String sessionId;

    /** 关联订单ID */
    private String orderId;

    /** 故障发生时间 */
    private LocalDateTime faultTime;

    /** 故障恢复时间 */
    private LocalDateTime recoverTime;

    /** 故障状态 */
    private FaultStatus faultStatus;

    /** 故障描述 */
    private String description;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

package bupt.evchargebackend.entity.pile;

import bupt.evchargebackend.entity.pile.enums.PileType;
import bupt.evchargebackend.entity.pile.enums.PowerState;
import bupt.evchargebackend.entity.pile.enums.WorkingState;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充电桩表。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
@Data
@TableName("charging_pile")
public class ChargingPile {

    /** 主键 */
    @TableId
    private String pileId;

    /** 充电桩编号，对外展示 */
    private String pileNo;

    /** 桩类型 */
    private PileType pileType;

    /** 额定功率（kW） */
    private Integer powerKw;

    /** 电源状态 */
    private PowerState powerState;

    /** 运行状态 */
    private WorkingState workingState;

    /** 当前充电会话ID */
    private String currentSessionId;

    /** 历史累计充电量（kWh） */
    private BigDecimal totalChargeKwh;

    /** 历史累计充电次数 */
    private Integer totalChargeCount;

    /** 历史累计充电时长（分钟） */
    private Integer totalChargeMinutes;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

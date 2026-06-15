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

    public String getPileId() {
        return pileId;
    }

    public void setPileId(String pileId) {
        this.pileId = pileId;
    }

    public String getPileNo() {
        return pileNo;
    }

    public void setPileNo(String pileNo) {
        this.pileNo = pileNo;
    }

    public PileType getPileType() {
        return pileType;
    }

    public void setPileType(PileType pileType) {
        this.pileType = pileType;
    }

    public Integer getPowerKw() {
        return powerKw;
    }

    public void setPowerKw(Integer powerKw) {
        this.powerKw = powerKw;
    }

    public PowerState getPowerState() {
        return powerState;
    }

    public void setPowerState(PowerState powerState) {
        this.powerState = powerState;
    }

    public WorkingState getWorkingState() {
        return workingState;
    }

    public void setWorkingState(WorkingState workingState) {
        this.workingState = workingState;
    }

    public String getCurrentSessionId() {
        return currentSessionId;
    }

    public void setCurrentSessionId(String currentSessionId) {
        this.currentSessionId = currentSessionId;
    }

    public BigDecimal getTotalChargeKwh() {
        return totalChargeKwh;
    }

    public void setTotalChargeKwh(BigDecimal totalChargeKwh) {
        this.totalChargeKwh = totalChargeKwh;
    }

    public Integer getTotalChargeCount() {
        return totalChargeCount;
    }

    public void setTotalChargeCount(Integer totalChargeCount) {
        this.totalChargeCount = totalChargeCount;
    }

    public Integer getTotalChargeMinutes() {
        return totalChargeMinutes;
    }

    public void setTotalChargeMinutes(Integer totalChargeMinutes) {
        this.totalChargeMinutes = totalChargeMinutes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

package bupt.evchargebackend.entity.user;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 车辆表。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
@Data
@TableName("car")
public class Car {

    /** 主键 */
    @TableId
    private String carId;

    /** 所属用户ID，外键 */
    private String userId;

    /** 车牌号 */
    private String carNo;

    /** 电池容量（kWh） */
    private BigDecimal batteryCapacityKwh;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

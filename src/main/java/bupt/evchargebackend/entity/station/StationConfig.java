package bupt.evchargebackend.entity.station;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 场站全局配置表。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
@Data
@TableName("station_config")
public class StationConfig {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 快充桩数量 */
    private Integer fastCount;

    /** 慢充桩数量 */
    private Integer slowCount;

    /** 每桩等候车位 */
    private Integer waitingSpotsPerPile;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

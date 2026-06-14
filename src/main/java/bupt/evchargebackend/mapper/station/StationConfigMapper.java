package bupt.evchargebackend.mapper.station;

import bupt.evchargebackend.entity.station.StationConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 场站全局配置 Mapper。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
@Mapper
public interface StationConfigMapper extends BaseMapper<StationConfig> {
}

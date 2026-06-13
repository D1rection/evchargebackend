package bupt.evchargebackend.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * @author Deng Chao
 * @since 2026-06-13
 */
@Configuration
@MapperScan("bupt.evchargebackend.mapper")
public class MyBatisPlusConfig {
}

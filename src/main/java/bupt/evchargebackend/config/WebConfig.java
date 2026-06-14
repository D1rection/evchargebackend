package bupt.evchargebackend.config;

import bupt.evchargebackend.common.jwt.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：JWT 拦截器注册 + CORS 整合。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    public WebConfig(JwtInterceptor jwtInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/v1/**", "/admin/**")
                .excludePathPatterns(
                        "/api/v1/account/create",
                        "/api/v1/account/login",
                        "/admin/account/create",
                        "/admin/account/login",
                        "/hello",
                        "/db/health"
                );
    }
}

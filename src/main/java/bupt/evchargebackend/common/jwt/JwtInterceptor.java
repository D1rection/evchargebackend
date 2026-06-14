package bupt.evchargebackend.common.jwt;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 令牌校验拦截器。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public JwtInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new bupt.evchargebackend.common.exception.BusinessException(401, "未登录或令牌已过期");
        }
        String token = auth.substring(7);
        try {
            String userId = jwtUtil.parseUserId(token);
            request.setAttribute("userId", userId);
            request.setAttribute("role", jwtUtil.parseRole(token));
            return true;
        } catch (Exception e) {
            throw new bupt.evchargebackend.common.exception.BusinessException(401, "令牌无效或已过期");
        }
    }
}

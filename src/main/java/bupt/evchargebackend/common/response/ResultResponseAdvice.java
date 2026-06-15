package bupt.evchargebackend.common.response;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 统一响应包装切面。
 * <p>
 * Controller 直接返回业务数据，本类自动包装为 {@link Result}。
 * 已为 {@link Result} 类型或 {@code byte[]} 等特殊类型时跳过。
 *
 * @author Deng Chao
 * @since 2026-06-15
 */
@RestControllerAdvice
public class ResultResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        // 跳过已包装的 Result、ResponseEntity 和 byte[] 等特殊类型
        Class<?> type = returnType.getParameterType();
        return !Result.class.isAssignableFrom(type)
                && !org.springframework.http.ResponseEntity.class.isAssignableFrom(type)
                && !byte[].class.isAssignableFrom(type);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        return Result.success(body);
    }
}

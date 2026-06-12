package bupt.evchargebackend.common.exception;

/**
 * 业务逻辑异常，携带应用层错误码。
 *
 * 在 Service 层抛出，由 {@link GlobalExceptionHandler} 捕获后转为结构化错误响应。
 *
 * @author Deng Chao
 * @since 2026-06-12
 */
public class BusinessException extends RuntimeException {

    private final int code;

    /**
     * @param code    应用层错误码
     * @param message 人类可读的错误描述
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** 从预定义错误码构造。 */
    public BusinessException(ErrorCode errorCode) {
        this(errorCode.getCode(), errorCode.getMessage());
    }

    /** 简写形式，错误码固定为 400。 */
    public BusinessException(String message) {
        this(400, message);
    }

    public int getCode() { return code; }
}

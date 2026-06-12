package bupt.evchargebackend.common.exception;

/**
 * 通用业务错误码。
 *
 * @author Deng Chao
 * @since 2026-06-12
 */
public enum ErrorCode {

    // ========== 认证授权 ==========
    UNAUTHORIZED(401, "未登录或令牌已过期"),
    FORBIDDEN(403, "无权限访问"),

    // ========== 资源 ==========
    RESOURCE_NOT_FOUND(404, "资源不存在"),

    // ========== 业务冲突 ==========
    DUPLICATE_DEVICE(409, "设备编号已存在"),
    PILE_ALREADY_RUNNING(409, "充电桩已启动"),
    PILE_ALREADY_STOPPED(409, "充电桩已关闭"),
    FAULT_ALREADY_RESOLVED(409, "故障已被处置"),

    // ========== 参数 ==========
    CONFIG_INVALID(400, "配置参数无效"),
    OPERATION_INVALID(400, "当前状态不允许此操作");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}

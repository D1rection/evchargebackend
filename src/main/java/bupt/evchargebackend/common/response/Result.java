package bupt.evchargebackend.common.response;

import bupt.evchargebackend.common.exception.BusinessException;

import java.util.function.Supplier;

/**
 * 统一 API 响应体包装。
 *
 * <p>所有接口（除报表导出文件外）必须返回此结构：
 * {@code {"code": 200, "msg": "success", "data": ...}}
 *
 * <h3>推荐用法</h3>
 * <pre>{@code
 * // 方式一：显式处理 BusinessException，错误码在代码中可见
 * return Result.of(() -> service.doSomething(param));
 *
 * // 方式二：手动控制
 * return Result.success(data);
 * return Result.error(409, "用户名已存在");
 * }</pre>
 *
 * @param <T> 数据载荷的类型
 *
 * @author Deng Chao
 * @since 2026-06-12
 */
public class Result<T> {

    private int code;
    private String msg;
    private T data;

    private Result() {}

    private Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /**
     * 执行业务逻辑并自动包装为 {@link Result}。
     *
     * <p>成功时返回 {@code code=200}，抛出 {@link BusinessException} 时
     * 自动转为对应的错误码和消息。其他异常继续向上传播。
     *
     * @param supplier 业务逻辑（通常是一个 service 方法调用）
     * @param <T>      返回值类型
     * @return 成功或业务错误的 Result
     */
    public static <T> Result<T> of(Supplier<T> supplier) {
        try {
            return success(supplier.get());
        } catch (BusinessException e) {
            return error(e.getCode(), e.getMessage());
        }
    }

    /**
     * 执行无返回值业务逻辑并自动包装为 {@link Result}。
     *
     * @param runnable 无返回值的业务逻辑
     * @return 成功时 {@code code=200, data=null}，业务异常时返回对应错误码
     */
    public static Result<Void> ofVoid(Runnable runnable) {
        try {
            runnable.run();
            return success();
        } catch (BusinessException e) {
            return error(e.getCode(), e.getMessage());
        }
    }

    /** 返回成功响应，携带数据。 */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    /** 返回成功响应，data 为 null。 */
    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    /** 返回错误响应，携带错误码和提示信息。 */
    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public int getCode() { return code; }
    public String getMsg() { return msg; }
    public T getData() { return data; }
}

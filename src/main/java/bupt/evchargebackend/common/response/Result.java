package bupt.evchargebackend.common.response;

/**
 * 统一 API 响应体包装。
 *
 * 除报表导出文件外，所有接口必须返回此结构：
 * {@code {"code": 200, "msg": "success", "data": ...}}
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

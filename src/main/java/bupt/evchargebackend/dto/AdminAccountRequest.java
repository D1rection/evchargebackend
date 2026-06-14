package bupt.evchargebackend.dto;

/**
 * 管理员账号请求（注册/登录共用）。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
public class AdminAccountRequest {

    private String userName;
    private String password;

    public AdminAccountRequest() {}

    public AdminAccountRequest(String userName, String password) {
        this.userName = userName;
        this.password = password;
    }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}

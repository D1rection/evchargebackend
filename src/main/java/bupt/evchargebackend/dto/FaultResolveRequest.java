package bupt.evchargebackend.dto;

/**
 * 故障处置请求。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
public class FaultResolveRequest {

    private Integer resolveCode;
    private String remark;

    public FaultResolveRequest() {}

    public Integer getResolveCode() { return resolveCode; }
    public void setResolveCode(Integer resolveCode) { this.resolveCode = resolveCode; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}

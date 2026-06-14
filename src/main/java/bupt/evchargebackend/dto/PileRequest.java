package bupt.evchargebackend.dto;

/**
 * 充电桩新增/编辑请求。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
public class PileRequest {

    private String pileNo;
    private String pileType;
    private Integer powerKw;

    public PileRequest() {}

    public String getPileNo() { return pileNo; }
    public void setPileNo(String pileNo) { this.pileNo = pileNo; }
    public String getPileType() { return pileType; }
    public void setPileType(String pileType) { this.pileType = pileType; }
    public Integer getPowerKw() { return powerKw; }
    public void setPowerKw(Integer powerKw) { this.powerKw = powerKw; }
}

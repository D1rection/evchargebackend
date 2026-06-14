package bupt.evchargebackend.dto;

/**
 * 场站配置请求。
 *
 * @author Deng Chao
 * @since 2026-06-14
 */
public class StationConfigRequest {

    private Integer fastCount;
    private Integer slowCount;
    private Integer waitingSpotsPerPile;

    public StationConfigRequest() {}

    public Integer getFastCount() { return fastCount; }
    public void setFastCount(Integer fastCount) { this.fastCount = fastCount; }
    public Integer getSlowCount() { return slowCount; }
    public void setSlowCount(Integer slowCount) { this.slowCount = slowCount; }
    public Integer getWaitingSpotsPerPile() { return waitingSpotsPerPile; }
    public void setWaitingSpotsPerPile(Integer waitingSpotsPerPile) { this.waitingSpotsPerPile = waitingSpotsPerPile; }
}

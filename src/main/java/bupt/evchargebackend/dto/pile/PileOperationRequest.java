package bupt.evchargebackend.dto.pile;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PileOperationRequest {

    @NotBlank(message = "pileId cannot be blank")
    private String pileId;

    public String getPileId() {
        return pileId;
    }

    public void setPileId(String pileId) {
        this.pileId = pileId;
    }
}

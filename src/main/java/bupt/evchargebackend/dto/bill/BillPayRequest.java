package bupt.evchargebackend.dto.bill;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BillPayRequest {

    @NotBlank(message = "billId cannot be blank")
    private String billId;

    public String getBillId() {
        return billId;
    }

    public void setBillId(String billId) {
        this.billId = billId;
    }
}

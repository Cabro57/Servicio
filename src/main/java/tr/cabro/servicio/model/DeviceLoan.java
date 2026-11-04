package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import tr.cabro.servicio.model.enums.LoanDirection;
import tr.cabro.servicio.model.enums.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Emanet kaydı ({@code device_loans}): bir cihazın bir müşteriye verilmesi ya da müşteriden
 * emanet alınması. {@link #returnedAt} boşken emanet açıktır.
 */
@Getter @Setter
public class DeviceLoan {

    private Long id;
    private Long deviceId;
    private Device device;          // JOIN ile doldurulur
    private Long customerId;
    private Customer customer;      // JOIN ile doldurulur
    private Long workOrderId;
    private LoanDirection direction;
    private LocalDateTime givenAt;
    private LocalDateTime dueAt;
    private BigDecimal deposit = BigDecimal.ZERO;
    private PaymentType depositPaymentType;
    private String conditionOut;
    private String accessories;
    private String note;
    private LocalDateTime returnedAt;
    private String conditionIn;
    private BigDecimal damageCharge = BigDecimal.ZERO;
    private BigDecimal depositRefund = BigDecimal.ZERO;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isOpen() {
        return returnedAt == null;
    }

    /** Açık ve iade/teslim tarihi geçmiş mi? */
    public boolean isOverdue() {
        return isOpen() && dueAt != null && dueAt.toLocalDate().isBefore(java.time.LocalDate.now());
    }

    public boolean hasDeposit() {
        return deposit != null && deposit.signum() > 0;
    }

    /** "EMN-12" — kasa raporu ve belgelerde görünen kısa numara. */
    public String getCode() {
        return "EMN-" + id;
    }
}

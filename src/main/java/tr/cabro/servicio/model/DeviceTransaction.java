package tr.cabro.servicio.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import tr.cabro.servicio.model.enums.DeviceTransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTransaction {

    private Long id;

    @ColumnName("device_id")
    private Long deviceId;

    @Nested("device")
    private Device device;

    private DeviceTransactionType type;

    @ColumnName("customer_id")
    private Long customerId;

    @Nested("customer")
    private Customer customer;

    @ColumnName("transaction_date")
    private LocalDateTime transactionDate;

    private BigDecimal price;

    @ColumnName("expertise_notes")
    private String expertiseNotes;

    @ColumnName("warranty_months")
    private Integer warrantyMonths;

    private String note;

    @ColumnName("is_deleted")
    private boolean deleted;

    @ColumnName("created_at")
    private LocalDateTime createdAt;

    @ColumnName("updated_at")
    private LocalDateTime updatedAt;

    @Override
    public String toString() {
        return type + " - " + device + " (" + transactionDate + ")";
    }
}

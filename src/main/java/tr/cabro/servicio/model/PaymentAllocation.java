package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import tr.cabro.servicio.model.enums.AllocationTargetType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Bir {@link Payment}'ın belirli bir belgeye (iş emri/satış) ne kadarının gittiği. */
@Getter @Setter
public class PaymentAllocation {

    private Long id;

    @ColumnName("payment_id")
    private Long paymentId;

    @ColumnName("target_type")
    private AllocationTargetType targetType;

    @ColumnName("target_id")
    private Long targetId;

    private BigDecimal amount;

    @ColumnName("created_at")
    private LocalDateTime createdAt;
}

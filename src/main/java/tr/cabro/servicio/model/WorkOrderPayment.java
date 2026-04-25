package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import tr.cabro.servicio.model.enums.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter
public class WorkOrderPayment {

    private Long id;

    @ColumnName("service_id")
    private Long serviceId;

    private BigDecimal amount;

    @ColumnName("payment_type")
    private PaymentType paymentType;

    private String note;

    @ColumnName("payment_date")
    private LocalDateTime paymentDate;

    @ColumnName("created_at")
    private LocalDateTime createdAt;
}
package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import tr.cabro.servicio.model.enums.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * "Para alındı/verildi" olayı — kasa raporunun kaynağı. Paranın hangi belgeye
 * (iş emri/satış) gittiği burada değil {@link PaymentAllocation}'da tutulur;
 * bir Payment birden fazla belgeye dağıtılabilir (cari tahsilat) veya hiç
 * dağıtılmamış (avans) olabilir.
 */
@Getter @Setter
public class Payment {

    private Long id;

    @ColumnName("customer_id")
    private Long customerId; // NULL = perakende/hızlı satış ödemesi

    private BigDecimal amount; // İade tahsilatlarında negatif olabilir

    @ColumnName("payment_type")
    private PaymentType paymentType;

    private String note;

    @ColumnName("payment_date")
    private LocalDateTime paymentDate;

    @ColumnName("created_at")
    private LocalDateTime createdAt;

    // DB kolonu değil; PaymentService.hydrate() ile doldurulur.
    private List<PaymentAllocation> allocations = new ArrayList<>();
}

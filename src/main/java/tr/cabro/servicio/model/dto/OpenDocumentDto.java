package tr.cabro.servicio.model.dto;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import tr.cabro.servicio.model.enums.AllocationTargetType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** {@code v_document_balances}'dan bir müşterinin kalan bakiyesi &gt; 0 olan belgesi — tahsilat dağıtım diyaloğu için. */
@Getter @Setter
public class OpenDocumentDto {

    @ColumnName("document_type")
    private AllocationTargetType documentType;

    @ColumnName("document_id")
    private Long documentId;

    @ColumnName("customer_id")
    private Long customerId;

    @ColumnName("document_date")
    private LocalDateTime documentDate;

    @ColumnName("total_amount")
    private BigDecimal totalAmount;

    @ColumnName("allocated_amount")
    private BigDecimal allocatedAmount;

    @ColumnName("remaining_amount")
    private BigDecimal remainingAmount;

    public String getDocumentLabel() {
        return documentType == AllocationTargetType.WORK_ORDER ? "SRV-" + documentId : "SAT-" + documentId;
    }
}

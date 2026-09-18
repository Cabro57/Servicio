package tr.cabro.servicio.model.dto;

import lombok.Getter;
import lombok.Setter;
import tr.cabro.servicio.model.enums.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Günlük kasa raporu — DB'den okunmaz, {@code SaleService.getDailyCashReport()} tarafından derlenir. */
@Getter @Setter
public class DailyCashReportDto {

    private LocalDate date;
    private Map<PaymentType, BigDecimal> breakdown;
    private BigDecimal total;
    private long saleCount;
    private long returnCount;
}

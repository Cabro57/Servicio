package tr.cabro.servicio.model.dto;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter
public class SummaryCardDto {
    private int totalRecords;
    private int activeRecords;
    private BigDecimal totalRevenue = BigDecimal.ZERO;  // Ciro (Gelen Para)
    private BigDecimal totalExpense = BigDecimal.ZERO;  // Gider (Parça Maliyeti)
    private BigDecimal totalProfit = BigDecimal.ZERO;   // Net Kâr
}
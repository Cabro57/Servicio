package tr.cabro.servicio.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
public class PartStatsDto {
    private long partVarietyCount;
    private long totalStock;
    private long criticalStockCount;
    private BigDecimal totalInventoryValue = BigDecimal.ZERO;
}

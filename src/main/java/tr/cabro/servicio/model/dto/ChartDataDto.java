package tr.cabro.servicio.model.dto;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter
public class ChartDataDto {
    private String label; // Örn: "Apple", "Tablet" veya Tarih "2026-04"
    private BigDecimal value; // Ciro veya Adet karşılığı
}
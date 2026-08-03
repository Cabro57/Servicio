package tr.cabro.servicio.model.dictionary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRate {

    @ColumnName("currency_code")
    private String currencyCode;

    @ColumnName("currency_name")
    private String currencyName;

    private BigDecimal rate;

    private String source;

    @ColumnName("updated_at")
    private LocalDateTime updatedAt;

    @Override
    public String toString() {
        return currencyCode;
    }
}

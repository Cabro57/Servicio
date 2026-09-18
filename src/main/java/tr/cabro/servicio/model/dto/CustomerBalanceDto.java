package tr.cabro.servicio.model.dto;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.math.BigDecimal;

/** {@code v_customer_balances}'dan bir müşterinin toplam borç/tahsilat/bakiyesi. */
@Getter @Setter
public class CustomerBalanceDto {

    @ColumnName("customer_id")
    private Long customerId;

    @ColumnName("total_debt")
    private BigDecimal totalDebt;

    @ColumnName("total_paid")
    private BigDecimal totalPaid;

    private BigDecimal balance;
}

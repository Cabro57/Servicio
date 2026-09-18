package tr.cabro.servicio.model.dto;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.math.BigDecimal;

/** {@code payment_allocations}'da hedef bazında tahsis toplamı — liste ekranlarında N+1 sorgudan kaçınmak için. */
@Getter @Setter
public class AllocationSumDto {

    @ColumnName("target_id")
    private Long targetId;

    private BigDecimal total;
}

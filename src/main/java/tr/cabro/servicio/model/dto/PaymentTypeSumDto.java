package tr.cabro.servicio.model.dto;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import tr.cabro.servicio.model.enums.PaymentType;

import java.math.BigDecimal;

/** Günlük kasa raporu — bir ödeme yöntemi için gün içi toplam. */
@Getter @Setter
public class PaymentTypeSumDto {

    @ColumnName("payment_type")
    private PaymentType paymentType;

    private BigDecimal total;
}

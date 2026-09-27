package tr.cabro.servicio.model.dto;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import tr.cabro.servicio.model.Payment;

/**
 * Hangi belgeye (iş emri/satış) tahsis edildiği bilgisini taşıyan ödeme — birden fazla belgenin
 * ödemelerini tek sorguda çekip belge bazında gruplamak için.
 */
@Getter @Setter
public class TargetPayment extends Payment {

    @ColumnName("target_id")
    private Long targetId;
}

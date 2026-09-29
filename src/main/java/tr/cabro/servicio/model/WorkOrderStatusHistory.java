package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import tr.cabro.servicio.model.enums.ServiceStatus;

import java.time.LocalDateTime;

/** Servis kaydının bir durum geçişi; durum tarihlerinin tek kaynağı (bkz. V27 migration). */
@Getter @Setter
public class WorkOrderStatusHistory {

    private Long id;

    @ColumnName("work_order_id")
    private Long workOrderId;

    private ServiceStatus status;

    /** Geçişin gerçekleştiği an; kullanıcı düzeltebilir. */
    @ColumnName("changed_at")
    private LocalDateTime changedAt;

    /** Satırın yazıldığı an; değişmez. */
    @ColumnName("created_at")
    private LocalDateTime createdAt;
}

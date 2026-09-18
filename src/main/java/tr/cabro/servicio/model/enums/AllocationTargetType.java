package tr.cabro.servicio.model.enums;

import org.jdbi.v3.core.enums.EnumByName;

/**
 * {@code payment_allocations.target_type} — polimorfik hedef, gerçek bir FK ile
 * kurulamadığı için uygulama katmanında yönetilir (bkz. {@code payment_allocations}
 * migration yorumu).
 */
@EnumByName
public enum AllocationTargetType {
    WORK_ORDER,
    SALE
}

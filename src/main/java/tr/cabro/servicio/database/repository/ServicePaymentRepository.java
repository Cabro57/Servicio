package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.WorkOrderPayment;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(WorkOrderPayment.class)
public interface ServicePaymentRepository {

    @SqlUpdate("INSERT INTO work_order_payments (service_id, amount, payment_type, note, payment_date, created_at) " +
            "VALUES (:serviceId, :amount, :paymentType, :note, :paymentDate, :createdAt)")
    @GetGeneratedKeys
    Long insertPayment(@BindBean WorkOrderPayment payment);

    @SqlUpdate("DELETE FROM work_order_payments WHERE id = :id")
    void deletePayment(@Bind("id") Long id);

    @SqlQuery("SELECT id, service_id, amount, payment_type, note, payment_date, created_at FROM work_order_payments WHERE id = :id")
    Optional<WorkOrderPayment> findById(@Bind("id") Long id);

    // Bir servise ait tüm ödemeleri tarihe göre sıralı getir
    @SqlQuery("SELECT id, service_id, amount, payment_type, note, payment_date, created_at FROM work_order_payments WHERE service_id = :serviceId ORDER BY payment_date ASC")
    List<WorkOrderPayment> findPaymentsByServiceId(@Bind("serviceId") Long serviceId);
}
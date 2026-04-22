package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.ServicePayment;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(ServicePayment.class)
public interface ServicePaymentRepository {

    @SqlUpdate("INSERT INTO service_payments (service_id, amount, payment_type, note, payment_date, created_at) " +
            "VALUES (:serviceId, :amount, :paymentType, :note, :paymentDate, :createdAt)")
    @GetGeneratedKeys
    int insertPayment(@BindBean ServicePayment payment);

    @SqlUpdate("DELETE FROM service_payments WHERE id = :id")
    void deletePayment(@Bind("id") int id);

    @SqlQuery("SELECT id, service_id, amount, payment_type, note, payment_date, created_at FROM service_payments WHERE id = :id")
    Optional<ServicePayment> findById(@Bind("id") int id);

    // Bir servise ait tüm ödemeleri tarihe göre sıralı getir
    @SqlQuery("SELECT id, service_id, amount, payment_type, note, payment_date, created_at FROM service_payments WHERE service_id = :serviceId ORDER BY payment_date ASC")
    List<ServicePayment> findPaymentsByServiceId(@Bind("serviceId") int serviceId);
}
package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.Payment;
import tr.cabro.servicio.model.dto.PaymentTypeSumDto;
import tr.cabro.servicio.model.enums.AllocationTargetType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * "Para alındı/verildi" olayı — kasa raporunun kaynağı. Paranın hangi belgeye
 * gittiği burada değil {@link PaymentAllocationRepository}'de tutulur.
 */
@RegisterBeanMapper(Payment.class)
public interface PaymentRepository {

    @SqlUpdate("INSERT INTO payments (customer_id, amount, payment_type, note, payment_date, created_at) " +
            "VALUES (:customerId, :amount, :paymentType, :note, :paymentDate, :createdAt)")
    @GetGeneratedKeys
    Long insert(@BindBean Payment payment);

    // Allocation'lar ON DELETE CASCADE ile birlikte silinir.
    @SqlUpdate("DELETE FROM payments WHERE id = :id")
    void delete(@Bind("id") Long id);

    @SqlQuery("SELECT id, customer_id, amount, payment_type, note, payment_date, created_at FROM payments WHERE id = :id")
    Optional<Payment> findById(@Bind("id") Long id);

    @SqlQuery("SELECT id, customer_id, amount, payment_type, note, payment_date, created_at FROM payments " +
            "WHERE customer_id = :customerId ORDER BY payment_date DESC")
    List<Payment> findByCustomerId(@Bind("customerId") Long customerId);

    // Bir belgeye (iş emri/satış) tahsis edilmiş ödemeler — WorkOrderPaymentsPanel'in bugünkü
    // "bu ödeme sadece bu iş emrine ait" akışı için (1 payment = 1 allocation varsayımıyla).
    @SqlQuery("SELECT p.id, p.customer_id, p.amount, p.payment_type, p.note, p.payment_date, p.created_at " +
            "FROM payments p JOIN payment_allocations pa ON pa.payment_id = p.id " +
            "WHERE pa.target_type = :targetType AND pa.target_id = :targetId ORDER BY p.payment_date ASC")
    List<Payment> findByTarget(@Bind("targetType") AllocationTargetType targetType, @Bind("targetId") Long targetId);

    // Ana sayfadaki "Bugünkü hareketler" listesi — tarih aralığındaki ödemeler, en yeni önce.
    @SqlQuery("SELECT id, customer_id, amount, payment_type, note, payment_date, created_at FROM payments " +
            "WHERE payment_date >= :start AND payment_date < :end ORDER BY payment_date DESC, id DESC LIMIT :limit")
    List<Payment> findByDateRange(@Bind("start") LocalDateTime start, @Bind("end") LocalDateTime end,
                                  @Bind("limit") int limit);

    // Günlük kasa raporu — yöntem bazında gün içi toplam (satış + servis tahsilatı + iade birlikte,
    // iade tutarları negatif olduğu için net kasa hareketini doğru yansıtır).
    @RegisterBeanMapper(PaymentTypeSumDto.class)
    @SqlQuery("SELECT payment_type, COALESCE(SUM(amount), 0.0) AS total FROM payments " +
            "WHERE payment_date >= :start AND payment_date < :end GROUP BY payment_type")
    List<PaymentTypeSumDto> sumByTypeForDateRange(@Bind("start") LocalDateTime start, @Bind("end") LocalDateTime end);
}

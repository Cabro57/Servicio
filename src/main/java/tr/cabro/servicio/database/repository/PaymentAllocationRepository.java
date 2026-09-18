package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.PaymentAllocation;
import tr.cabro.servicio.model.dto.AllocationSumDto;
import tr.cabro.servicio.model.enums.AllocationTargetType;

import java.math.BigDecimal;
import java.util.List;

/** Bir {@code Payment}'ın hangi belgeye (iş emri/satış) ne kadar tahsis edildiği. */
@RegisterBeanMapper(PaymentAllocation.class)
public interface PaymentAllocationRepository {

    @SqlUpdate("INSERT INTO payment_allocations (payment_id, target_type, target_id, amount, created_at) " +
            "VALUES (:paymentId, :targetType, :targetId, :amount, :createdAt)")
    @GetGeneratedKeys
    Long insert(@BindBean PaymentAllocation allocation);

    @SqlUpdate("DELETE FROM payment_allocations WHERE id = :id")
    void delete(@Bind("id") Long id);

    @SqlQuery("SELECT id, payment_id, target_type, target_id, amount, created_at FROM payment_allocations WHERE payment_id = :paymentId")
    List<PaymentAllocation> findByPaymentId(@Bind("paymentId") Long paymentId);

    @SqlQuery("SELECT id, payment_id, target_type, target_id, amount, created_at FROM payment_allocations " +
            "WHERE target_type = :targetType AND target_id = :targetId")
    List<PaymentAllocation> findByTarget(@Bind("targetType") AllocationTargetType targetType, @Bind("targetId") Long targetId);

    @SqlQuery("SELECT COUNT(*) FROM payment_allocations WHERE payment_id = :paymentId")
    long countByPaymentId(@Bind("paymentId") Long paymentId);

    @SqlQuery("SELECT COALESCE(SUM(amount), 0.0) FROM payment_allocations WHERE target_type = :targetType AND target_id = :targetId")
    BigDecimal sumByTarget(@Bind("targetType") AllocationTargetType targetType, @Bind("targetId") Long targetId);

    // Liste ekranlarında (FormSales vb.) her satır için ayrı sorgu atmamak için toplu sürüm.
    @RegisterBeanMapper(AllocationSumDto.class)
    @SqlQuery("SELECT target_id, COALESCE(SUM(amount), 0.0) AS total FROM payment_allocations " +
            "WHERE target_type = :targetType AND target_id IN (<targetIds>) GROUP BY target_id")
    List<AllocationSumDto> sumByTargets(@Bind("targetType") AllocationTargetType targetType, @BindList("targetIds") List<Long> targetIds);
}

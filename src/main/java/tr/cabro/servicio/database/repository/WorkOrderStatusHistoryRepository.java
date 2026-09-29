package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.WorkOrderStatusHistory;
import tr.cabro.servicio.model.enums.ServiceStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(WorkOrderStatusHistory.class)
public interface WorkOrderStatusHistoryRepository {

    @SqlUpdate("INSERT INTO work_order_status_history (work_order_id, status, changed_at, created_at) " +
            "VALUES (:workOrderId, :status, :changedAt, :createdAt)")
    @GetGeneratedKeys
    Long insert(@Bind("workOrderId") Long workOrderId,
                @Bind("status") ServiceStatus status,
                @Bind("changedAt") LocalDateTime changedAt,
                @Bind("createdAt") LocalDateTime createdAt);

    // Kronolojik sıra; aynı andaki geçişlerde yazılış sırası (id) belirleyicidir.
    @SqlQuery("SELECT * FROM work_order_status_history WHERE work_order_id = :workOrderId ORDER BY changed_at, id")
    List<WorkOrderStatusHistory> findByWorkOrderId(@Bind("workOrderId") Long workOrderId);

    @SqlQuery("SELECT * FROM work_order_status_history WHERE id = :id")
    Optional<WorkOrderStatusHistory> findById(@Bind("id") Long id);

    @SqlUpdate("UPDATE work_order_status_history SET changed_at = :changedAt WHERE id = :id")
    void updateChangedAt(@Bind("id") Long id, @Bind("changedAt") LocalDateTime changedAt);

    @SqlUpdate("DELETE FROM work_order_status_history WHERE id = :id")
    void delete(@Bind("id") Long id);
}

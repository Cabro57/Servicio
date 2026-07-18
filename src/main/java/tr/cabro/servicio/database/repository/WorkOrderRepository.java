package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.enums.ServiceStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(WorkOrder.class)
public interface WorkOrderRepository {

    @SqlUpdate("INSERT INTO work_orders (customer_id, device_id, technician_id, reported_fault, " +
            "urgency_status, service_status, warranty_end_date, delivery_date, created_at, updated_at) " +
            "VALUES (:customerId, :deviceId, :technicianId, :reportedFault, " +
            ":urgencyStatus, :serviceStatus, :warrantyEndDate, :deliveryDate, :createdAt, :updatedAt)")
    @GetGeneratedKeys
    Long insert(@BindBean WorkOrder workOrder);

    @SqlUpdate("UPDATE work_orders SET " +
            "customer_id=:customerId, device_id=:deviceId, technician_id=:technicianId, " +
            "reported_fault=:reportedFault, updated_at=:updatedAt " +
            "WHERE id=:id")
    void update(@BindBean WorkOrder workOrder);

    @SqlUpdate("UPDATE work_orders SET service_status=:status, delivery_date=:deliveryDate, updated_at=:updatedAt WHERE id=:id")
    void updateStatus(@Bind("id") Long id,
                      @Bind("status") ServiceStatus status,
                      @Bind("deliveryDate") LocalDateTime deliveryDate,
                      @Bind("updatedAt") LocalDateTime updatedAt);

    @SqlQuery("SELECT * FROM work_orders WHERE id = :id")
    Optional<WorkOrder> findById(@Bind("id") Long id);

    @SqlUpdate("DELETE FROM work_orders WHERE id = :id")
    void delete(@Bind("id") Long id);

    @SqlQuery("SELECT * FROM work_orders ORDER BY created_at DESC")
    List<WorkOrder> findAll();

    @SqlQuery("SELECT * FROM work_orders WHERE customer_id = :customerId ORDER BY created_at DESC")
    List<WorkOrder> findByCustomerId(@Bind("customerId") Long customerId);

    @SqlQuery("SELECT * FROM work_orders WHERE device_id = :deviceId ORDER BY created_at DESC")
    List<WorkOrder> findByDeviceId(@Bind("deviceId") Long deviceId);

    @SqlQuery("SELECT DISTINCT wo.* FROM work_orders wo " +
            "INNER JOIN work_order_items woi ON woi.service_id = wo.id " +
            "WHERE woi.part_id = :partId ORDER BY wo.created_at DESC")
    List<WorkOrder> findByPartId(@Bind("partId") Long partId);

    @SqlQuery("SELECT * FROM work_orders WHERE service_status IN (<statuses>) ORDER BY created_at DESC")
    List<WorkOrder> findByStatuses(@BindList("statuses") List<ServiceStatus> statuses);

    @SqlQuery("SELECT * FROM work_orders WHERE service_status NOT IN (<statuses>) ORDER BY created_at DESC")
    List<WorkOrder> findByStatusesExcluded(@BindList("statuses") List<ServiceStatus> statuses);

    @SqlQuery("SELECT s.* FROM work_orders s " +
            "WHERE s.reported_fault LIKE :search " +
            "OR EXISTS (SELECT 1 FROM work_order_notes sn WHERE sn.service_id = s.id AND sn.note LIKE :search) " +
            "ORDER BY s.created_at DESC")
    List<WorkOrder> search(@Bind("search") String searchTerm);

    // =========================================================================
    // SAYFALAMA (LIMIT/OFFSET) — DB-tabanlı liste ekranları ve dashboard için
    // =========================================================================

    @SqlQuery("SELECT * FROM work_orders ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    List<WorkOrder> findAllPaged(@Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("SELECT COUNT(*) FROM work_orders")
    long countAll();

    @SqlQuery("SELECT s.* FROM work_orders s " +
            "WHERE s.reported_fault LIKE :search " +
            "OR EXISTS (SELECT 1 FROM work_order_notes sn WHERE sn.service_id = s.id AND sn.note LIKE :search) " +
            "ORDER BY s.created_at DESC LIMIT :limit OFFSET :offset")
    List<WorkOrder> searchPaged(@Bind("search") String searchTerm, @Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("SELECT COUNT(*) FROM work_orders s " +
            "WHERE s.reported_fault LIKE :search " +
            "OR EXISTS (SELECT 1 FROM work_order_notes sn WHERE sn.service_id = s.id AND sn.note LIKE :search)")
    long countSearch(@Bind("search") String searchTerm);

    @SqlQuery("SELECT * FROM work_orders WHERE service_status IN (<statuses>) ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    List<WorkOrder> findByStatusesPaged(@BindList("statuses") List<ServiceStatus> statuses,
                                        @Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("SELECT COUNT(*) FROM work_orders WHERE service_status IN (<statuses>)")
    long countByStatuses(@BindList("statuses") List<ServiceStatus> statuses);

    @SqlQuery("SELECT * FROM work_orders WHERE service_status NOT IN (<statuses>) ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    List<WorkOrder> findByStatusesExcludedPaged(@BindList("statuses") List<ServiceStatus> statuses,
                                                 @Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("SELECT COUNT(*) FROM work_orders WHERE service_status NOT IN (<statuses>)")
    long countByStatusesExcluded(@BindList("statuses") List<ServiceStatus> statuses);

    // "Bekleyen Tahsilatlar": kalan tutar (kalem toplamı - ödeme toplamı) > 0 olan iş emirleri.
    // Kalan tutar bir DB kolonu değil; iki alt sorguyla (item toplamı, ödeme toplamı) hesaplanır.
    @SqlQuery("SELECT wo.* FROM work_orders wo " +
            "LEFT JOIN (SELECT service_id, SUM(unit_price * quantity) AS total FROM work_order_items GROUP BY service_id) i ON i.service_id = wo.id " +
            "LEFT JOIN (SELECT service_id, SUM(amount) AS paid FROM work_order_payments GROUP BY service_id) p ON p.service_id = wo.id " +
            "WHERE COALESCE(i.total, 0) - COALESCE(p.paid, 0) > 0 " +
            "ORDER BY wo.created_at DESC LIMIT :limit OFFSET :offset")
    List<WorkOrder> findWithDebtPaged(@Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("SELECT COUNT(*) FROM work_orders wo " +
            "LEFT JOIN (SELECT service_id, SUM(unit_price * quantity) AS total FROM work_order_items GROUP BY service_id) i ON i.service_id = wo.id " +
            "LEFT JOIN (SELECT service_id, SUM(amount) AS paid FROM work_order_payments GROUP BY service_id) p ON p.service_id = wo.id " +
            "WHERE COALESCE(i.total, 0) - COALESCE(p.paid, 0) > 0")
    long countWithDebt();
}
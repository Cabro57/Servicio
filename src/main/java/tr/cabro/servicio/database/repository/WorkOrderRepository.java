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

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(WorkOrder.class)
public interface WorkOrderRepository {

    @SqlUpdate("INSERT INTO work_orders (customer_id, device_id, technician_id, reported_fault, detected_fault, " +
            "urgency_status, service_status, warranty_end_date, delivery_date, created_at, updated_at) " +
            "VALUES (:customerId, :deviceId, :technicianId, :reportedFault, :detectedFault, " +
            ":urgencyStatus, :serviceStatus, :warrantyEndDate, :deliveryDate, :createdAt, :updatedAt)")
    @GetGeneratedKeys
    Long insert(@BindBean WorkOrder workOrder);

    @SqlUpdate("UPDATE work_orders SET customer_id=:customerId, device_id=:deviceId, technician_id=:technicianId, " +
            "reported_fault=:reportedFault, detected_fault=:detectedFault, " +
            "urgency_status=:urgencyStatus, service_status=:serviceStatus, " +
            "warranty_end_date=:warrantyEndDate, delivery_date=:deliveryDate, updated_at=:updatedAt " +
            "WHERE id=:id")
    void update(@BindBean WorkOrder workOrder);

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

    @SqlQuery("SELECT * FROM work_orders WHERE service_status IN (<statuses>) ORDER BY created_at DESC")
    List<WorkOrder> findByStatuses(@BindList("statuses") List<ServiceStatus> statuses);

    @SqlQuery("SELECT * FROM work_orders WHERE service_status NOT IN (<statuses>) ORDER BY created_at DESC")
    List<WorkOrder> findByStatusesExcluded(@BindList("statuses") List<ServiceStatus> statuses);

    @SqlQuery("SELECT s.* FROM work_orders s " +
            "LEFT JOIN devices d ON d.id = s.device_id " +
            "WHERE d.brand LIKE :search OR d.model LIKE :search OR d.serial_no LIKE :search " +
            "   OR s.reported_fault LIKE :search " +
            "   OR EXISTS (SELECT 1 FROM service_notes sn WHERE sn.service_id = s.id AND sn.note LIKE :search) " +
            "ORDER BY s.created_at DESC")
    List<WorkOrder> search(@Bind("search") String searchTerm);
}
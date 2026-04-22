package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transaction;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.model.enums.ServiceStatus;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(Service.class)
public interface ServiceRepository {

    @SqlUpdate("INSERT INTO services (customer_id, device_id, technician_id, reported_fault, detected_fault, " +
            "action_taken, urgency_status, service_status, warranty_end_date, delivery_date, created_at, updated_at) " +
            "VALUES (:customerId, :deviceId, :technicianId, :reportedFault, :detectedFault, " +
            ":actionTaken, :urgencyStatus, :serviceStatus, :warrantyEndDate, :deliveryDate, :createdAt, :updatedAt)")
    @GetGeneratedKeys
    int insertService(@BindBean Service service);

    @SqlUpdate("UPDATE services SET customer_id=:customerId, device_id=:deviceId, technician_id=:technicianId, " +
            "reported_fault=:reportedFault, detected_fault=:detectedFault, action_taken=:actionTaken, " +
            "urgency_status=:urgencyStatus, service_status=:serviceStatus, " +
            "warranty_end_date=:warrantyEndDate, delivery_date=:deliveryDate, updated_at=:updatedAt " +
            "WHERE id=:id")
    void updateService(@BindBean Service service);

    @SqlQuery("SELECT * FROM services WHERE id = :id")
    Optional<Service> findById(@Bind("id") int id);

    @SqlUpdate("DELETE FROM services WHERE id = :id")
    void deleteById(@Bind("id") int id);

    @SqlQuery("SELECT * FROM services ORDER BY created_at DESC")
    List<Service> findAll();

    @SqlQuery("SELECT * FROM services WHERE customer_id = :customerId ORDER BY created_at DESC")
    List<Service> findByCustomerId(@Bind("customerId") int customerId);

    @SqlQuery("SELECT * FROM services WHERE device_id = :deviceId ORDER BY created_at DESC")
    List<Service> findByDeviceId(@Bind("deviceId") int deviceId);

    @SqlQuery("SELECT * FROM services WHERE service_status IN (<statuses>) ORDER BY created_at DESC")
    List<Service> findByStatuses(@BindList("statuses") List<ServiceStatus> statuses);

    @SqlQuery("SELECT * FROM services WHERE service_status NOT IN (<statuses>) ORDER BY created_at DESC")
    List<Service> findByStatusesExcluded(@BindList("statuses") List<ServiceStatus> statuses);

    @SqlQuery("SELECT s.* FROM services s " +
            "LEFT JOIN devices d ON d.id = s.device_id " +
            "WHERE d.brand LIKE :search OR d.model LIKE :search OR d.serial_no LIKE :search " +
            "   OR s.reported_fault LIKE :search " +
            "   OR EXISTS (SELECT 1 FROM service_notes sn WHERE sn.service_id = s.id AND sn.note LIKE :search) " +
            "ORDER BY s.created_at DESC")
    List<Service> search(@Bind("search") String searchTerm);

    @Transaction
    default Service saveFullService(Service service, boolean isUpdate) {
        if (!isUpdate) {
            int id = insertService(service);
            service.setId(id);
        } else {
            updateService(service);
        }
        return service;
    }

    @Transaction
    default void deleteService(int id) {
        deleteById(id);
    }
}
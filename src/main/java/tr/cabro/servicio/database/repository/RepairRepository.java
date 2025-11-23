package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transaction;
import tr.cabro.servicio.model.AddedPart;
import tr.cabro.servicio.model.Service;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(Service.class)
@RegisterBeanMapper(AddedPart.class)
public interface RepairRepository {

    // --- SERVICE CRUD ---

    @SqlUpdate("INSERT INTO services (customer_id, created_at, delivery_at, device_type, device_brand, device_model, " +
            "device_serial, device_password, device_accessory, labor_cost, paid, payment_type, " +
            "warranty_date, maintenance_date, reported_fault, detected_fault, action_taken, urgency_status, service_status, Notes) " +
            "VALUES (:customerId, :createdAt, :deliveryAt, :deviceType, :deviceBrand, :deviceModel, " +
            ":deviceSerial, :devicePassword, :deviceAccessory, :laborCost, :paid, :paymentType, " +
            ":warrantyDate, :maintenanceDate, :reportedFault, :detectedFault, :actionTaken, :urgencyStatus, :serviceStatus, :notes)")
    @GetGeneratedKeys
    int insertService(@BindBean Service service);

    @SqlUpdate("UPDATE services SET customer_id=:customerId, created_at=:createdAt, delivery_at=:deliveryAt, " +
            "device_type=:deviceType, device_brand=:deviceBrand, device_model=:deviceModel, device_serial=:deviceSerial, " +
            "device_password=:devicePassword, device_accessory=:deviceAccessory, labor_cost=:laborCost, paid=:paid, " +
            "payment_type=:paymentType, warranty_date=:warrantyDate, maintenance_date=:maintenanceDate, " +
            "reported_fault=:reportedFault, detected_fault=:detectedFault, action_taken=:actionTaken, " +
            "urgency_status=:urgencyStatus, service_status=:serviceStatus, Notes=:notes WHERE id=:id")
    void updateService(@BindBean Service service);

    @SqlUpdate("DELETE FROM services WHERE id = :id")
    void deleteService(@Bind("id") int id);

    // Eğer snake_case -> camelCase eşleşmesinde sorun yaşanırsa sorguyu şöyle güncelleyebilirsiniz:
    // SELECT id, customer_id as customerId, created_at as createdAt ... FROM services
    @SqlQuery("SELECT * FROM services WHERE id = :id")
    Optional<Service> findServiceById(@Bind("id") int id);

    @SqlQuery("SELECT * FROM services ORDER BY created_at DESC")
    List<Service> findAllServices();

    @SqlQuery("SELECT * FROM services WHERE customer_id = :customerId ORDER BY created_at DESC")
    List<Service> findServicesByCustomerId(@Bind("customerId") int customerId);

    @SqlQuery("SELECT * FROM services WHERE device_serial LIKE :search OR device_brand LIKE :search OR device_model LIKE :search ORDER BY created_at DESC")
    List<Service> searchServices(@Bind("search") String search);


    // --- EKLENEN PARÇA (ADDED PART) İŞLEMLERİ ---

    @SqlBatch("INSERT INTO added_part (service_id, series_no, brand, name, supplier_id, device_type, model, amount, " +
            "purchase_price, sale_price, warranty_period, purchase_date, description, created_at) " +
            "VALUES (:serviceId, :serialNo, :brand, :name, :supplierId, :deviceType, :models, :amount, " +
            ":purchasePrice, :sellingPrice, :warrantyPeriod, :purchaseDate, :description, :createdAt)")
    void insertParts(@BindBean List<AddedPart> parts);

    // Java modelinde 'serialNo' ve 'models' var, DB'de 'series_no' ve 'model' var.
    // BeanMapper'ın hatasız çalışması için 'AS' (Alias) kullanarak isimleri eşitliyoruz.
    @SqlQuery("SELECT id, service_id, series_no as serialNo, brand, name, supplier_id, device_type, model as models, " +
            "amount, purchase_price, sale_price as sellingPrice, warranty_period, purchase_date, description, created_at " +
            "FROM added_part WHERE service_id = :serviceId")
    List<AddedPart> findPartsByServiceId(@Bind("serviceId") int serviceId);

    @SqlUpdate("DELETE FROM added_part WHERE service_id = :serviceId")
    void deletePartsByServiceId(@Bind("serviceId") int serviceId);

    @SqlUpdate("DELETE FROM added_part WHERE id = :id")
    void deletePart(@Bind("id") int id);


    // --- TRANSACTION ---

    @Transaction
    default Service saveFullService(Service service, List<AddedPart> parts, boolean isUpdate) {
        int serviceId;
        if (!isUpdate) {
            serviceId = insertService(service);
            service.setId(serviceId);
        } else {
            updateService(service);
            serviceId = service.getId();
            deletePartsByServiceId(serviceId);
        }

        if (parts != null && !parts.isEmpty()) {
            // Parçalara servis ID'sini atıyoruz
            for (AddedPart p : parts) {
                p.setServiceId(serviceId);
            }
            insertParts(parts);
        }
        return service;
    }
}
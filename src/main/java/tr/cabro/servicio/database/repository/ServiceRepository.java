package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.KeyColumn;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.config.ValueColumn;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.locator.UseClasspathSqlLocator;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transaction;
import tr.cabro.servicio.model.AddedPart;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.model.dto.DashboardStats;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.reports.ServiceFinanceRecord;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RegisterBeanMapper(Service.class)
@RegisterBeanMapper(AddedPart.class)
public interface ServiceRepository {

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

    @SqlQuery("SELECT id, customer_id, created_at, delivery_at, device_type, device_brand, device_model, " +
            "device_serial, device_password, device_accessory, labor_cost, paid, payment_type, " +
            "warranty_date, maintenance_date, reported_fault, detected_fault, action_taken, " +
            "urgency_status, service_status, Notes FROM services WHERE id = :id")
    Optional<Service> findById(@Bind("id") int id);

    @SqlQuery("SELECT id, customer_id, created_at, delivery_at, device_type, device_brand, device_model, " +
            "device_serial, device_password, device_accessory, labor_cost, paid, payment_type, " +
            "warranty_date, maintenance_date, reported_fault, detected_fault, action_taken, " +
            "urgency_status, service_status, Notes FROM services ORDER BY created_at DESC")
    List<Service> findServices();

    @SqlQuery("SELECT id, customer_id, created_at, delivery_at, device_type, device_brand, device_model, " +
            "device_serial, device_password, device_accessory, labor_cost, paid, payment_type, " +
            "warranty_date, maintenance_date, reported_fault, detected_fault, action_taken, " +
            "urgency_status, service_status, Notes FROM services WHERE customer_id = :customerId ORDER BY created_at DESC")
    List<Service> findByCustomerId(@Bind("customerId") int customerId);

    @SqlQuery("SELECT id, customer_id, created_at, delivery_at, device_type, device_brand, device_model, " +
            "device_serial, device_password, device_accessory, labor_cost, paid, payment_type, " +
            "warranty_date, maintenance_date, reported_fault, detected_fault, action_taken, " +
            "urgency_status, service_status, Notes FROM services " +
            "WHERE service_status IN (<statuses>) ORDER BY created_at DESC")
    List<Service> findByServiceStatuses(@BindList("statuses") List<ServiceStatus> statuses);

    @SqlQuery("SELECT id, customer_id, created_at, delivery_at, device_type, device_brand, device_model, " +
            "device_serial, device_password, device_accessory, labor_cost, paid, payment_type, " +
            "warranty_date, maintenance_date, reported_fault, detected_fault, action_taken, " +
            "urgency_status, service_status, Notes FROM services " +
            "WHERE service_status NOT IN (<statuses>) ORDER BY created_at DESC")
    List<Service> findByServiceStatusesExcluded(@BindList("statuses") List<ServiceStatus> statuses);

    @SqlQuery("SELECT id, customer_id, created_at, delivery_at, device_type, device_brand, device_model, " +
            "device_serial, device_password, device_accessory, labor_cost, paid, payment_type, " +
            "warranty_date, maintenance_date, reported_fault, detected_fault, action_taken, " +
            "urgency_status, service_status, Notes FROM services WHERE device_serial LIKE :search OR device_brand LIKE :search OR device_model LIKE :search ORDER BY created_at DESC")
    List<Service> search(@Bind("search") String search);


    // --- EKLENEN PARÇA (ADDED PART) İŞLEMLERİ ---

    @SqlUpdate("INSERT INTO added_part (service_id, part_barcode, is_stock_tracked, series_no, brand, name, supplier_id, device_type, model, amount, " +
            "purchase_price, sale_price, warranty_period, purchase_date, description, created_at) " +
            "VALUES (:serviceId, :partBarcode, :stockTracked, :serialNo, :brand, :name, :supplierId, :deviceType, :model, :amount, " +
            ":purchasePrice, :sellingPrice, :warrantyPeriod, :purchaseDate, :description, :createdAt)")
    @GetGeneratedKeys
    int insertPart(@BindBean AddedPart part);

    @SqlUpdate("UPDATE added_part SET " +
            "series_no = :serialNo, " +
            "brand = :brand, " +
            "supplier_id = :supplierId, " +
            "name = :name, " +
            "device_type = :deviceType, " +
            "model = :model, " +
            "purchase_price = :purchasePrice, " +
            "sale_price = :sellingPrice, " +
            "amount = :amount, " +
            "warranty_period = :warrantyPeriod, " +
            "purchase_date = :purchaseDate, " +
            "description = :description " +
            "WHERE id = :id")
    void updatePart(@BindBean AddedPart part);

    @SqlQuery("SELECT id, service_id, series_no, brand, name, supplier_id, device_type, model, " +
            "amount, purchase_price, sale_price, warranty_period, purchase_date, description, created_at " +
            "FROM added_part WHERE service_id = :serviceId")
    List<AddedPart> findPartsByServiceId(@Bind("serviceId") int serviceId);

    @SqlUpdate("DELETE FROM added_part WHERE service_id = :serviceId")
    void deletePartsByServiceId(@Bind("serviceId") int serviceId);

    @SqlUpdate("DELETE FROM added_part WHERE id = :id")
    void deletePart(@Bind("id") int id);

    @SqlQuery("SELECT COALESCE(SUM(sale_price * amount), 0.0) FROM added_part WHERE service_id = :serviceId")
    double calculateTotalPartsCost(@Bind("serviceId") int serviceId);

    @SqlQuery("SELECT service_id, COALESCE(SUM(sale_price * amount), 0.0) AS total FROM added_part WHERE service_id IN (<serviceIds>) GROUP BY service_id")
    @KeyColumn("service_id")
    @ValueColumn("total")
    Map<Integer, Double> getPartsCostsByServiceIds(@BindList("serviceIds") List<Integer> serviceIds);

    // --- TRANSACTION ---

    @Transaction
    default Service saveFullService(Service service, List<AddedPart> parts, boolean isUpdate) {
        int serviceId;
        if (!isUpdate) {
            serviceId = insertService(service);
            service.setId(serviceId);
        } else {
            updateService(service);
        }
        return service;
    }

    @Transaction
    default void deleteServiceCompletely(int id) {
        // Hiyerarşik olarak en alttan (child) en üste (parent) doğru silme işlemi yapılır
        deletePartsByServiceId(id);
        deleteService(id);
    }

    @UseClasspathSqlLocator
    @SqlQuery
    @RegisterBeanMapper(ServiceFinanceRecord.class)
    List<ServiceFinanceRecord> financeRecords();

    @UseClasspathSqlLocator
    @SqlQuery
    @RegisterBeanMapper(DashboardStats.class)
    @RegisterBeanMapper(DashboardStats.class)
    DashboardStats dashboardStats();
}
package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.WorkOrderItem;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(WorkOrderItem.class)
public interface ServiceItemRepository {

    @SqlUpdate("INSERT INTO work_order_items (service_id, item_type, source_type, part_id, labor_id, item_name, used_serial_no, quantity, purchase_price, unit_price, tax_rate) " +
            "VALUES (:serviceId, :itemType, :sourceType, :partId, :laborId, :itemName, :usedSerialNo, :quantity, :purchasePrice, :unitPrice, :taxRate)")
    @GetGeneratedKeys
    Long insert(@BindBean WorkOrderItem item);

    @SqlUpdate("UPDATE work_order_items SET item_type=:itemType, source_type=:sourceType, part_id=:partId, labor_id=:laborId, " +
            "item_name=:itemName, used_serial_no=:usedSerialNo, quantity=:quantity, purchase_price=:purchasePrice, unit_price=:unitPrice, tax_rate=:taxRate " +
            "WHERE id=:id")
    void update(@BindBean WorkOrderItem item);

    @SqlUpdate("DELETE FROM work_order_items WHERE id = :id")
    void delete(@Bind("id") Long id);

    @SqlUpdate("DELETE FROM work_order_items WHERE service_id = :serviceId")
    void deleteByServiceId(@Bind("serviceId") Long serviceId);

    @SqlQuery("SELECT id, service_id, item_type, source_type, part_id, labor_id, item_name, " +
            "used_serial_no, quantity, purchase_price, unit_price, tax_rate " +
            "FROM work_order_items WHERE service_id = :serviceId")
    List<WorkOrderItem> findByServiceId(@Bind("serviceId") Long serviceId);

    @SqlQuery("SELECT id, service_id, item_type, source_type, part_id, labor_id, item_name, " +
            "used_serial_no, quantity, purchase_price, unit_price, tax_rate " +
            "FROM work_order_items WHERE id = :id")
    Optional<WorkOrderItem> findById(@Bind("id") Long id);
}
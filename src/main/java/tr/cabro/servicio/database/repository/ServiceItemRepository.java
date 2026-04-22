package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.ServiceItem;

import java.util.List;

@RegisterBeanMapper(ServiceItem.class)
public interface ServiceItemRepository {

    @SqlUpdate("INSERT INTO service_items (service_id, item_type, source_type, part_id, labor_id, item_name, used_serial_no, quantity, purchase_price, unit_price, tax_rate) " +
            "VALUES (:serviceId, :itemType, :sourceType, :partId, :laborId, :itemName, :usedSerialNo, :quantity, :purchasePrice, :unitPrice, :taxRate)")
    @GetGeneratedKeys
    int insert(@BindBean ServiceItem item);

    @SqlUpdate("UPDATE service_items SET item_type=:itemType, source_type=:sourceType, part_id=:partId, labor_id=:laborId, " +
            "item_name=:itemName, used_serial_no=:usedSerialNo, quantity=:quantity, purchase_price=:purchasePrice, unit_price=:unitPrice, tax_rate=:taxRate " +
            "WHERE id=:id")
    void update(@BindBean ServiceItem item);

    @SqlUpdate("DELETE FROM service_items WHERE id = :id")
    void delete(@Bind("id") int id);

    @SqlUpdate("DELETE FROM service_items WHERE service_id = :serviceId")
    void deleteByServiceId(@Bind("serviceId") int serviceId);

    @SqlQuery("SELECT id, service_id, item_type, source_type, part_id, labor_id, item_name, " +
            "used_serial_no, quantity, purchase_price, unit_price, tax_rate " +
            "FROM service_items WHERE service_id = :serviceId")
    List<ServiceItem> findByServiceId(@Bind("serviceId") int serviceId);
}
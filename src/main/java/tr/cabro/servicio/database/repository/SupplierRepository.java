package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.Supplier;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(Supplier.class)
public interface SupplierRepository {

    @SqlUpdate("INSERT INTO suppliers (name, business_name, tax_number, tax_office, email, phone, address, note, created_at, updated_at) " +
            "VALUES (:name, :businessName, :taxNumber, :taxOffice, :email, :phone, :address, :note, :createdAt, :updatedAt)")
    @GetGeneratedKeys
    Long insert(@BindBean Supplier supplier);

    @SqlUpdate("UPDATE suppliers SET name=:name, business_name=:businessName, tax_number=:taxNumber, " +
            "tax_office=:taxOffice, email=:email, phone=:phone, address=:address, note=:note, updated_at=:updatedAt WHERE id=:id")
    void update(@BindBean Supplier supplier);

    // --- SOFT DELETE İŞLEMLERİ ---
    @SqlUpdate("UPDATE suppliers SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
    void delete(@Bind("id") Long id);

    @SqlUpdate("UPDATE suppliers SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id IN (<ids>)")
    void deleteByIds(@BindList("ids") List<Long> ids);

    // --- SEÇME (SELECT) İŞLEMLERİ ---
    // SELECT * yerine performansı ve güvenilirliği artırmak için kolon isimlerini açıkça yazmak kurumsal bir best-practice'dir.
    @SqlQuery("SELECT id, name, business_name, tax_number, tax_office, email, phone, address, note, is_deleted, created_at, updated_at FROM suppliers WHERE id = :id AND is_deleted = 0")
    Optional<Supplier> findById(@Bind("id") Long id);

    @SqlQuery("SELECT id, name, business_name, tax_number, tax_office, email, phone, address, note, is_deleted, created_at, updated_at FROM suppliers WHERE id IN (<ids>) AND is_deleted = 0")
    List<Supplier> findByIds(@BindList("ids") List<Long> ids);

    @SqlQuery("SELECT id, name, business_name, tax_number, tax_office, email, phone, address, note, is_deleted, created_at, updated_at FROM suppliers WHERE is_deleted = 0 ORDER BY name")
    List<Supplier> findAll();

    // UI tarafındaki arama panellerinde kullanmanız için kullanışlı bir search metodu
    @SqlQuery("SELECT id, name, business_name, tax_number, tax_office, email, phone, address, note, is_deleted, created_at, updated_at FROM suppliers WHERE is_deleted = 0 AND " +
            "(name LIKE :search OR business_name LIKE :search OR tax_number LIKE :search OR phone LIKE :search) " +
            "ORDER BY name")
    List<Supplier> search(@Bind("search") String searchTerm);
}
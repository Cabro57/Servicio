package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.Supplier;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(Supplier.class)
public interface SupplierRepository {

    @SqlUpdate("INSERT INTO suppliers (name, business_name, id_no, tax_no, tax_office, email, phone, address, note, created_at) " +
            "VALUES (:name, :businessName, :idNo, :taxNo, :taxOffice, :email, :phone, :address, :notes, :created_at)")
    @GetGeneratedKeys
    int insert(@BindBean Supplier supplier);

    @SqlUpdate("UPDATE suppliers SET name=:name, business_name=:businessName, id_no=:idNo, tax_no=:taxNo, " +
            "tax_office=:taxOffice, email=:email, phone=:phone, address=:address, note=:notes, created_at=:created_at WHERE id=:id")
    void update(@BindBean Supplier supplier);

    @SqlUpdate("DELETE FROM suppliers WHERE id = :id")
    void delete(@Bind("id") int id);

    @SqlQuery("SELECT * FROM suppliers WHERE id = :id")
    Optional<Supplier> findById(@Bind("id") int id);

    @SqlQuery("SELECT * FROM suppliers ORDER BY name")
    List<Supplier> findAll();
}
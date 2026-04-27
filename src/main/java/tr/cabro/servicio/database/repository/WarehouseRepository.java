package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.Warehouse;

import java.util.List;

// TODO servis sınıfı yok eklnecek
public interface WarehouseRepository {

    @SqlUpdate("INSERT INTO warehouses (name) VALUES (:name)")
    @GetGeneratedKeys
    Long insert(String name);

    @SqlQuery("SELECT id, name FROM warehouses WHERE id = :id")
    Warehouse findById();

    @SqlQuery("SELECT id, name FROM warehouse")
    List<Warehouse> findAll();


}

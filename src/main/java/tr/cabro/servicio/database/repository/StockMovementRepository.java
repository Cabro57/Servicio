package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.StockMovement;

public interface StockMovementRepository {
    @SqlUpdate("INSERT INTO stock_movements " +
            "(product_id, warehouse_id, quantity, type, reference_type, reference_id) " +
            "VALUES (:partId, :warehouseId, :quantity, :type, :referenceType, :referenceId)")
    void insert(@BindBean StockMovement stockMovement);

    @SqlQuery("SELECT COALESCE(SUM(quantity), 0) " +
            "FROM stock_movements " +
            "WHERE product_id = :partId")
    Integer getStock(@Bind("partId") Long partId);
}

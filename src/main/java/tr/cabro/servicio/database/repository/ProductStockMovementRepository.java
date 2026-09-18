package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.ProductStockMovement;

public interface ProductStockMovementRepository {
    @SqlUpdate("INSERT INTO product_stock_movements (product_id, quantity, type, reference_type, reference_id) " +
            "VALUES (:productId, :quantity, :type, :referenceType, :referenceId)")
    void insert(@BindBean ProductStockMovement stockMovement);

    @SqlQuery("SELECT COALESCE(SUM(quantity), 0) FROM product_stock_movements WHERE product_id = :productId")
    Integer getStock(@Bind("productId") Long productId);
}

package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.SaleItem;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(SaleItem.class)
public interface SaleItemRepository {

    String COLUMNS = "id, sale_id, product_id, item_name, quantity, purchase_price, unit_price, sale_currency, " +
            "unit_price_original, line_discount_type, line_discount_value, line_total, created_at, source_sale_item_id ";

    @SqlUpdate("INSERT INTO sale_items (sale_id, product_id, item_name, quantity, purchase_price, unit_price, sale_currency, " +
            "unit_price_original, line_discount_type, line_discount_value, line_total, created_at, source_sale_item_id) " +
            "VALUES (:saleId, :productId, :itemName, :quantity, :purchasePrice, :unitPrice, :saleCurrency, " +
            ":unitPriceOriginal, :lineDiscountType, :lineDiscountValue, :lineTotal, :createdAt, :sourceSaleItemId)")
    @GetGeneratedKeys
    Long insert(@BindBean SaleItem item);

    @SqlUpdate("DELETE FROM sale_items WHERE id = :id")
    void delete(@Bind("id") Long id);

    @SqlQuery("SELECT " + COLUMNS + "FROM sale_items WHERE id = :id")
    Optional<SaleItem> findById(@Bind("id") Long id);

    @SqlQuery("SELECT " + COLUMNS + "FROM sale_items WHERE sale_id = :saleId ORDER BY id ASC")
    List<SaleItem> findBySaleId(@Bind("saleId") Long saleId);

    /**
     * Bir orijinal satış kalemine karşılık zaten iade edilmiş toplam miktar — İADE satırlarında
     * quantity NEGATİF tutulduğu için sonuç negatif çıkar (ör. 1 adet iade edilmişse -1).
     * "Kalan iade edilebilir miktar" = orijinal quantity + bu sonuç.
     */
    @SqlQuery("SELECT COALESCE(SUM(quantity), 0) FROM sale_items WHERE source_sale_item_id = :sourceSaleItemId")
    int sumReturnedQuantity(@Bind("sourceSaleItemId") Long sourceSaleItemId);
}

package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.Part;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(Part.class)
public interface PartRepository {

    // :model -> Part sınıfındaki getModel() metodunu çağırır.
    @SqlUpdate("INSERT INTO part (barcode, brand, supplier_id, name, device_type, model, purchase_price, sale_price, stock, min_stock, warranty_period, purchase_date, description, created_at) " +
            "VALUES (:barcode, :brand, :supplierId, :name, :deviceType, :model, :purchasePrice, :salePrice, :stock, :minStock, :warrantyPeriod, :purchaseDate, :description, :createdAt)")
    void insert(@BindBean Part part);

    @SqlUpdate("UPDATE part SET brand=:brand, name=:name, supplier_id=:supplierId, device_type=:deviceType, model=:model, " +
            "purchase_price=:purchasePrice, sale_price=:salePrice, stock=:stock, min_stock=:minStock, " +
            "warranty_period=:warrantyPeriod, purchase_date=:purchaseDate, description=:description WHERE barcode=:barcode")
    void update(@BindBean Part part);

    @SqlUpdate("DELETE FROM part WHERE barcode = :barcode")
    void delete(@Bind("barcode") String barcode);

    @SqlUpdate("DELETE FROM part WHERE barcode IN (<barcodes>)")
    void deleteByBarcodes(@BindList("barcodes") List<String> barcodes);

    @SqlQuery("SELECT * FROM part WHERE barcode = :barcode")
    Optional<Part> findByBarcode(@Bind("barcode") String barcode);

    @SqlQuery("SELECT * FROM part ORDER BY name")
    List<Part> findAll();

    // Kritik stok seviyesinin altındaki ürünleri getirir
    @SqlQuery("SELECT * FROM part WHERE stock < min_stock")
    List<Part> findBelowMinStock();

    // Barkodun var olup olmadığını kontrol eder (Hızlı sorgu)
    @SqlQuery("SELECT count(1) FROM part WHERE barcode = :barcode")
    boolean existsByBarcode(@Bind("barcode") String barcode);

    @SqlUpdate("UPDATE part SET stock = stock - :amount WHERE barcode = :barcode AND stock >= :amount")
    int decreaseStockAtomically(@Bind("barcode") String barcode, @Bind("amount") int amount);

    @SqlUpdate("UPDATE part SET stock = stock + :amount WHERE barcode = :barcode")
    void increaseStockAtomically(@Bind("barcode") String barcode, @Bind("amount") int amount);
}
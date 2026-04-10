package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter
public class AddedPart {

    private int id;

    private int serviceId; // Hangi servis kaydına ait

    @ColumnName("part_barcode")
    private String partBarcode; // Stoktaki parçanın gerçek barkodu

    @ColumnName("is_stock_tracked")
    private boolean stockTracked;

    private transient boolean returnToStockOnDelete;

    @ColumnName("series_no")
    private String seriesNo; // Parça Seri Numarası
    private String brand; // Ürün Markası
    private String name; // Ürün Adı
    private Integer supplierId; // Tedarikçi
    private String deviceType; // Ürün Cihaz Türü
    private String model; // Ürün uyumlu modelleri
    private int amount; // Parça Adeti
    private double purchasePrice; //Parça Alış Fiyatı

    @ColumnName("sale_price")
    private double sellingPrice; // Parça Satış Fiyatı

    private int warrantyPeriod; // Garanti Süresi

    private LocalDate purchaseDate; // Alınma Tarihi
    private String description; // Açıklama - Ürün hakkında not

    private LocalDateTime createdAt;

    public AddedPart() {
        this.createdAt = LocalDateTime.now();
    }

    public AddedPart(Part data, Integer amount) {
        this(data);
        this.amount = amount;
    }

    public AddedPart(Part data) {
        this();
        this.partBarcode = data.getBarcode(); // Asıl referans bağlandı!
        this.stockTracked = true;

        this.brand = data.getBrand();
        this.name = data.getName();
        this.supplierId = data.getSupplierId();
        this.deviceType = data.getDeviceType();
        this.model = data.getModel();
        this.amount = 1;
        this.purchasePrice = data.getPurchasePrice();
        this.sellingPrice = data.getSalePrice();
        this.warrantyPeriod = data.getWarrantyPeriod();
        this.purchaseDate = data.getPurchaseDate();
        this.description = data.getDescription();
    }

    public double getTotal() {
        return sellingPrice * amount;
    }

}



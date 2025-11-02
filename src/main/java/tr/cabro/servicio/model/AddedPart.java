package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter
public class AddedPart {

    private int id;

    private int serviceId; // Hangi servis kaydına ait
    private String serial_no; // Parça Seri Numarası
    private String brand; // Ürün Markası
    private String name; // Ürün Adı
    private int supplier_id; // Tedarikçi
    private String device_type; // Ürün Cihaz Türü
    private String models; // Ürün uyumlu modelleri
    private int amount; // Parça Adeti
    private double purchasePrice; //Parça Alış Fiyatı
    private double sellingPrice; // Parça Satış Fiyatı

    private int warranty_period; // Garanti Süresi

    private LocalDate purchase_date; // Alınma Tarihi
    private String description; // Açıklama - Ürün hakkında not

    private LocalDateTime created_at;

    public AddedPart() {}

    public AddedPart(Part data, Integer amount) {
        this(data);
        this.amount = amount;
    }

    public AddedPart(Part data) {
        this.brand = data.getBrand();
        this.name = data.getName();
        this.supplier_id = data.getSupplier_id();
        this.device_type = data.getDevice_type();
        this.models = data.getModels();
        this.amount = 1;
        this.purchasePrice = data.getPurchase_price();
        this.sellingPrice = data.getSale_price();
        this.warranty_period = data.getWarranty_period();
        this.purchase_date = data.getPurchase_date();
        this.description = data.getDescription();
        this.created_at = LocalDateTime.now();
    }

    public double getTotal() {
        return sellingPrice * amount;
    }

}



package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter
public class AddedPart {

    private int id;

    private int serviceId; // Hangi servis kaydına ait
    private String serialNo; // Parça Seri Numarası
    private String brand; // Ürün Markası
    private String name; // Ürün Adı
    private int supplierId; // Tedarikçi
    private String deviceType; // Ürün Cihaz Türü
    private String models; // Ürün uyumlu modelleri
    private int amount; // Parça Adeti
    private double purchasePrice; //Parça Alış Fiyatı
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

        this.brand = data.getBrand();
        this.name = data.getName();
        this.supplierId = data.getSupplierId();
        this.deviceType = data.getDeviceType();
        this.models = data.getModels();
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



package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter
public class Part {

    private String barcode; // Ürün kodu ya da barkod

    private String brand; // Ürün Markası
    private String name; // Ürün Adı
    private int supplierId; // Tedarikçi

    private String deviceType; // Ürün Cihaz Türü
    private String model; // Ürün uyumlu modelleri

    private double purchasePrice; // Alış Fiyatı
    private double salePrice; // Satış Fiyatı
    private int stock; // Stok miktarı
    private int minStock; // Minimum Stok Miktarı

    private int warrantyPeriod; // Garanti Süresi

    private LocalDate purchaseDate; // Alınma Tarihi
    private String description; // Açıklama - Ürün hakkında not

    private LocalDateTime createdAt;

    public Part(String barcode, String brand, String name) {
        this.barcode = barcode;
        this.brand = brand;
        this.name = name;
        this.createdAt = LocalDateTime.now();
    }

    public Part() {
        this.createdAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return brand.toUpperCase() + " " + name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Part other = (Part) obj;
        return barcode.equals(other.barcode);
    }

    @Override
    public int hashCode() {
        return barcode.hashCode();
    }

}

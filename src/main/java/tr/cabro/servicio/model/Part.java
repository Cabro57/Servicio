package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Getter @Setter
public class Part {

    private String barcode; // Ürün kodu ya da barkod

    private String brand; // Ürün Markası
    private String name; // Ürün Adı
    private int supplier_id; // Tedarikçi

    private String device_type; // Ürün Cihaz Türü
    private String models; // Ürün uyumlu modelleri

    private double purchase_price; // Alış Fiyatı
    private double sale_price; // Satış Fiyatı
    private int stock; // Stok miktarı
    private int minStock; // Minimum Stok Miktarı

    private int warranty_period; // Garanti Süresi

    private LocalDate purchase_date; // Alınma Tarihi
    private String description; // Açıklama - Ürün hakkında not

    private LocalDateTime created_at;

    public Part(String barcode, String brand, String name) {
        this.barcode = barcode;
        this.brand = brand;
        this.name = name;
        this.created_at = LocalDateTime.now();
    }

    public Part() {
        this.created_at = LocalDateTime.now();
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

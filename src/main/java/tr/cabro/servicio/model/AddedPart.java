package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter @Setter
public class AddedPart {

    private int id;

    private int serviceId; // Hangi servis kaydına ait
    private String barcode; // parça kodu
    private String serial_no; // Parça Seri Numarası
    private String name; // Parça Adı
    private int amount; // Parça Adeti
    private double purchasePrice; //Parça Alış Fiyatı
    private double sellingPrice; // Parça Satış Fiyatı

    private LocalDate addedDate;

    public AddedPart(String barcode, int amount, double price, int serviceId) {
        this.barcode = barcode;
        this.amount = amount;
        this.sellingPrice = price;
        this.serviceId = serviceId;
    }

    public AddedPart() {}

    public double getTotal() {
        return sellingPrice * amount;
    }

}



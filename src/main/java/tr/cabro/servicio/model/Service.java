package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Getter @Setter
public class Service {

    private int id;

    // Müşteri Bilgileri
    private int customer_id;
    private LocalDate created_at;
    private LocalDate delivery_at;

    // Cihaz Bilgileri
    private String device_type; // Cihaz Türü
    private String device_brand; // Marka
    private String device_model; // Model
    private String device_serial; // Seri numara / IMEI
    private String device_password; // Şifre
    private String device_accessory; // Aksesuar

    // Fiyat Bilgileri
    // private double material_cost; // Malzeme Ücretleri
    private double labor_cost; // İşçilik Ücretleri
    private double paid; // Ödenen
    private String payment_type; // Ödeme Türü

    // Garanti Bakım Bilgileri
    private LocalDate warranty_date;
    private LocalDate maintenance_date;

    // Arıza ve İşlem Bilgileri
    private String reported_fault; // Bildirilen Arıza
    private String detected_fault; // Tespit Edilen Arıza
    private String action_taken; // Yapılan İşlem

    // Parça Değişimi ve Notlar
    private String Notes;

    // Durum
    private String urgency_status; // Aciliyet
    private String service_status;


    public Service(int customer, String type, String brand, String model) {
        this.customer_id = customer;
        this.device_type = type;
        this.device_brand = brand;
        this.device_model = model;
    }

    public Service() {}


    public void setWarranty_end_date(LocalDate warranty_end_date) {
        this.warranty_date = warranty_end_date;
    }

    public void setWarranty_end_date(String warranty_end_date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        this.warranty_date = LocalDate.parse(warranty_end_date, formatter);
    }

    public void setDelivery_at(LocalDate delivery_date) {
        this.delivery_at = delivery_date;
    }

    public void setDelivery_at(String delivery_date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        this.delivery_at = LocalDate.parse(delivery_date, formatter);
    }

    public void setCreated_at(LocalDate created_at) {
        this.created_at = created_at;
    }

    public void setCreated_at(String created_at) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        this.created_at  = LocalDate.parse(created_at, formatter);
    }


}

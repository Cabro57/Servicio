package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
public class Service {

    private int id;

    // Müşteri Bilgileri
    private int customer_id;
    private LocalDateTime created_at;
    private LocalDateTime delivery_at;

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
    private PaymentType payment_type; // Ödeme Türü

    // Garanti Bakım Bilgileri
    private LocalDateTime warranty_date;
    private LocalDateTime maintenance_date;

    // Arıza ve İşlem Bilgileri
    private String reported_fault; // Bildirilen Arıza
    private String detected_fault; // Tespit Edilen Arıza
    private String action_taken; // Yapılan İşlem

    // Parça Değişimi ve Notlar
    private String Notes;

    // Durum
    private String urgency_status; // Aciliyet
    private ServiceStatus service_status;


    public Service(int customer, String type, String brand, String model) {
        this.customer_id = customer;
        this.device_type = type;
        this.device_brand = brand;
        this.device_model = model;
    }

    public Service() {}


    public Device getDevice() {
        Device device = new Device();

        device.setSerial(device_serial);
        device.setModel(device_model);
        device.setBrand(device_brand);
        device.setType(device_type);

        return device;
    }

}

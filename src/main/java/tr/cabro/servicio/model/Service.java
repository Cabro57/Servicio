package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import tr.cabro.servicio.model.enums.PaymentType;
import tr.cabro.servicio.model.enums.ServiceStatus;

import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter
public class Service {

    private int id;

    // Müşteri Bilgileri
    private Integer customerId;
    private Customer customer;
    private LocalDateTime createdAt;
    private LocalDateTime deliveryAt;

    // Cihaz Bilgileri
    private String deviceType; // Cihaz Türü
    private String deviceBrand; // Marka
    private String deviceModel; // Model
    private String deviceSerial; // Seri numara / IMEI
    private String devicePassword; // Şifre
    private String deviceAccessory; // Aksesuar

    // Fiyat Bilgileri
    // private double material_cost; // Malzeme Ücretleri
    private double laborCost; // İşçilik Ücretleri
    private double paid; // Ödenen
    private PaymentType paymentType; // Ödeme Türü

    // Garanti Bakım Bilgileri
    private LocalDateTime warrantyDate;
    private LocalDateTime maintenanceDate;

    // Arıza ve İşlem Bilgileri
    private String reportedFault; // Bildirilen Arıza
    private String detectedFault; // Tespit Edilen Arıza
    private String actionTaken; // Yapılan İşlem

    // Parça Değişimi ve Notlar
    private String Notes;

    // Durum
    private String urgencyStatus; // Aciliyet
    private ServiceStatus serviceStatus;

    private double totalPartsCost;

    private List<AddedPart> addedParts;


    public Service(int customer, String type, String brand, String model) {
        this.customerId = customer;
        this.deviceType = type;
        this.deviceBrand = brand;
        this.deviceModel = model;
    }

    public Service() {}


    public Device getDevice() {
        Device device = new Device();

        device.setSerial(deviceSerial);
        device.setModel(deviceModel);
        device.setBrand(deviceBrand);
        device.setType(deviceType);

        return device;
    }

    public double getRemainingAmount() {
        return (getLaborCost() + totalPartsCost) - getPaid();
    }

}

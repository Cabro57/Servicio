package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class Device {

    private String type; // Cihaz Türü
    private String brand; // Marka
    private String model; // Model
    private String serial; // Seri numara / IMEI

    public Device(Service service) {

        this.type = service.getDeviceType();
        this.brand = service.getDeviceBrand();
        this.model = service.getDeviceModel();

        this.serial = service.getDeviceSerial();

    }

    public Device() {
    }


    @Override
    public String toString() {
        return "Device{" +
                "type='" + type + '\'' +
                ", brand='" + brand + '\'' +
                ", model='" + model + '\'' +
                ", serial='" + serial + '\'' +
                '}';
    }
}

package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class Device {

    private String type; // Cihaz Türü
    private String brand; // Marka
    private String model; // Model
    private String serial; // Seri numara / IMEI
}

package tr.cabro.servicio.model.dictionary;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class DeviceType {
    private int id;
    private String name;

    @Override
    public String toString() { return name; }
}
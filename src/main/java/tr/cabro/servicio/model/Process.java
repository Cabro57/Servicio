package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class Process {
    private String name;
    private double price;

    public Process(String processName, double price) {
        this.name = processName;
        this.price = price;
    }

}

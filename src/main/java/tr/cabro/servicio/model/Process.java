package tr.cabro.servicio.model;

import eu.okaeri.configs.OkaeriConfig;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class Process extends OkaeriConfig {
    private String name;
    private String comment;
    private double price;

    public Process(String name, String comment, double price) {
        this.name = name;
        this.comment = comment;
        this.price = price;
    }
}
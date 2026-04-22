package tr.cabro.servicio.model;

import eu.okaeri.configs.OkaeriConfig;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
public class Process extends OkaeriConfig {
    private String name;
    private String comment;
    private BigDecimal price;

    public Process(String name, String comment, BigDecimal price) {
        this.name = name;
        this.comment = comment;
        this.price = price;
    }
}
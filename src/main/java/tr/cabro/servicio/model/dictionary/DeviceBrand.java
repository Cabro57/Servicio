package tr.cabro.servicio.model.dictionary;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class DeviceBrand {
    private Long id;
    private String name;

    @Override
    public String toString() { return name; }
}
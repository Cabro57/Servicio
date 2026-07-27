package tr.cabro.servicio.model.dictionary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class PartCategory {
    private Long id;
    private String name;
    private Integer partCount;

    @Override
    public String toString() { return name; }
}

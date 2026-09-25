package tr.cabro.servicio.model.dictionary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceBrand {
    private Long id;
    private String name;
    /** Cihaz kaydı sayısı: türe göre listede o türdeki, genel listede tüm cihazlar. */
    private Integer deviceCount;
    /** Markanın bağlı olduğu türler (genel listede tümü, türe göre listede diğerleri), virgülle. */
    private String typeNames;

    public DeviceBrand(Long id, String name) {
        this(id, name, 0, null);
    }

    public int devices() { return deviceCount == null ? 0 : deviceCount; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof DeviceBrand other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    @Override
    public String toString() { return name; }
}

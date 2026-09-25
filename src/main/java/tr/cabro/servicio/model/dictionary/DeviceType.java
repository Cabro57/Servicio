package tr.cabro.servicio.model.dictionary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceType {
    private Long id;
    private String name;
    private Integer brandCount;
    /** Bu türdeki cihaz kaydı sayısı (yalnızca sözlük listesinde doldurulur). */
    private Integer deviceCount;
    /** Bu türe özel hazır işçilik sayısı (yalnızca sözlük listesinde doldurulur). */
    private Integer laborCount;

    public DeviceType(Long id, String name, Integer brandCount) {
        this(id, name, brandCount, 0, 0);
    }

    public int devices() { return deviceCount == null ? 0 : deviceCount; }
    public int labors() { return laborCount == null ? 0 : laborCount; }
    public int brands() { return brandCount == null ? 0 : brandCount; }

    // Kimlik id'dir: listeler yenilenince seçili kayıt yeni nesneyle de eşleşsin.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof DeviceType other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    @Override
    public String toString() { return name; }
}

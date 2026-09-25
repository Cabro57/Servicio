package tr.cabro.servicio.model.dictionary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tr.cabro.servicio.model.enums.CategoryScope;

import java.util.Objects;

/** Parça ve ürün kategorisi; {@link #scope} hangi katalogda seçilebildiğini söyler. */
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class PartCategory {
    private Long id;
    private String name;
    private Integer partCount;
    private Integer productCount;
    private CategoryScope scope = CategoryScope.BOTH;

    public int parts() { return partCount == null ? 0 : partCount; }
    public int products() { return productCount == null ? 0 : productCount; }

    public boolean appliesTo(CategoryScope catalog) {
        return scope == null || scope.appliesTo(catalog);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof PartCategory other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }

    @Override
    public String toString() { return name; }
}

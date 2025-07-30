package tr.cabro.servicio.importer;

import lombok.Getter;

import java.util.List;

@Getter
public class CsvImportResult<T> {
    private final List<T> data;
    private final List<String> errors;

    public CsvImportResult(List<T> data, List<String> errors) {
        this.data = data;
        this.errors = errors;
    }

}

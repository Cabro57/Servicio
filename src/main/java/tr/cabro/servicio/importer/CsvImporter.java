package tr.cabro.servicio.importer;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import tr.cabro.servicio.importer.mapper.BaseCsvMapper;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class CsvImporter<T> {

    /**
     * CSV dosyasını satır satır okuyup mapper ile modele dönüştürür.
     * İlk satırı başlık olarak atlar.
     * Hatalı satırlar hata listesine eklenir.
     */
    public CsvImportResult<T> importFromCsv(String filepath, BaseCsvMapper<T> mapper) throws IOException {
        List<T> list = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(Files.newInputStream(Paths.get(filepath)), StandardCharsets.UTF_8))) {

            String[] row;
            int lineNumber = 0;
            boolean firstLine = true;

            while ((row = reader.readNext()) != null) {
                lineNumber++;

                if (row[0].equalsIgnoreCase("id")) {
                    firstLine = false; // Başlık satırını atla
                    continue;
                }

                try {
                    T obj = mapper.map(row);
                    list.add(obj);
                } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
                    errors.add("Satır " + lineNumber + ": " + e.getMessage());
                } catch (Exception e) {
                    errors.add("Satır " + lineNumber + ": Beklenmeyen hata - " + e.getMessage());
                }
            }
        } catch (CsvException e) {
            throw new IOException("CSV okunurken hata oluştu", e);
        }

        return new CsvImportResult<>(list, errors);
    }
}

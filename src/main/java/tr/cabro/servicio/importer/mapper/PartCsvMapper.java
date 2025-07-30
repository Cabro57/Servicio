package tr.cabro.servicio.importer.mapper;

import tr.cabro.servicio.model.Part;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class PartCsvMapper extends BaseCsvMapper<Part> {

    static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Override
    protected int expectedFieldCount() {
        return 15;
    }

    @Override
    protected Part mapRow(String[] fields) {
        Part p = new Part();

        p.setOldId(parseInt(fields[0]));
        p.setName(clean(fields[1]));

        String barcode = clean(fields[2]);
        if (barcode == null || barcode.isEmpty()) {
            // Barkod boşsa UUID ile benzersiz barkod oluştur
            barcode = UUID.randomUUID().toString();
        }
        p.setBarcode(barcode);

        p.setSupplier_name(clean(fields[4]));
        p.setBrand("");
        p.setModels(clean(fields[5] + " " + fields[6]));
        p.setDevice_type(clean(fields[7]));

        String warrantyPeriod = clean(fields[8]);

        int warrantyMonths = 0;
        if (!warrantyPeriod.equalsIgnoreCase("Yok")) {
            // Örnek: "3 Ay" -> 3 kısmını al
            String[] parts = warrantyPeriod.split(" ");
            try {
                warrantyMonths = Integer.parseInt(parts[0]);
            } catch (NumberFormatException e) {
                warrantyMonths = 0;
            }
        }

        p.setWarranty_period(warrantyMonths);

        p.setPurchase_price(parseDouble(fields[9]));
        p.setSale_price(parseDouble(fields[10]));
        p.setStock(parseInt(fields[11]));
        p.setDescription(clean(fields[12]));
        p.setPurchase_date(parseDate(fields[13], DATE_TIME_FORMATTER));
        p.setCreated_at(parseDateTime(fields[13], DATE_TIME_FORMATTER));

        return p;
    }
}

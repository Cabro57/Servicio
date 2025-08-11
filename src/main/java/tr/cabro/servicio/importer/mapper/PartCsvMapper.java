package tr.cabro.servicio.importer.mapper;

import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.util.barcode.BarcodeGenerator;

import java.time.format.DateTimeFormatter;
import java.util.Map;

public class PartCsvMapper extends BaseCsvMapper<Part> {

    static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final BarcodeGenerator barcodeGenerator;

    private final Map<Integer, String> oldSerialMap;

    public PartCsvMapper(BarcodeGenerator barcodeGenerator, Map<Integer, String> oldSerialMap) {
        this.barcodeGenerator = barcodeGenerator;
        this.oldSerialMap = oldSerialMap;
    }

    @Override
    protected int expectedFieldCount() {
        return 15;
    }

    @Override
    protected Part mapRow(String[] fields) {
        Part p = new Part();

        int oldId = parseInt(fields[0]);
        p.setOldId(oldId);
        p.setName(clean(fields[1]));

        // eski seri_no (eski sistemde part üzerine yazılıyordu)
        String oldSerial = clean(fields[2]);
        if (oldSerial != null && !oldSerial.isEmpty()) {
            oldSerialMap.put(oldId, oldSerial);
        }

        // artık eski seri_no'yu PART.barcode olarak koymuyoruz; yeni barcode üretiyoruz
        p.setBarcode(barcodeGenerator.generate());

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

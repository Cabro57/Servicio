package tr.cabro.servicio.importer.mapper;

import tr.cabro.servicio.model.AddedPart;

import java.time.format.DateTimeFormatter;

public class AddedPartCsvMapper extends BaseCsvMapper<AddedPart> {

    static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Override
    protected int expectedFieldCount() {
        return 6;
    }

    @Override
    protected AddedPart mapRow(String[] fields) {
        AddedPart ap = new AddedPart();

        ap.setId(parseInt(fields[0]));
        ap.setServiceId(parseInt(fields[1]));
        ap.setBarcode(clean(fields[2]));
        ap.setAmount(1);
        ap.setPurchasePrice(parseDouble(fields[3]));
        ap.setSellingPrice(parseDouble(fields[4]));
        ap.setAddedDate(parseDateTime(fields[5], DATE_TIME_FORMATTER));


        return ap;
    }
}

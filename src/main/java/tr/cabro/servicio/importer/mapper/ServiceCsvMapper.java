package tr.cabro.servicio.importer.mapper;

import tr.cabro.servicio.model.Service;

import java.time.format.DateTimeFormatter;

public class ServiceCsvMapper extends BaseCsvMapper<Service> {

    static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    protected int expectedFieldCount() {
        return 23;
    }

    @Override
    protected Service mapRow(String[] fields) {
        Service s = new Service();
        s.setId(parseInt(fields[0]));

        s.setCustomer_id(parseInt(fields[1]));
        s.setDevice_type(clean(fields[2]));
        s.setDevice_brand(clean(fields[3]));
        s.setDevice_model(clean(fields[4]));
        s.setDevice_serial(clean(fields[5]));
        s.setDevice_password(clean(fields[6]));
        s.setUrgency_status(clean(fields[7]));
        s.setDevice_accessory(clean(fields[8]));
        s.setReported_fault(clean(fields[9]));
        s.setDetected_fault(clean(fields[10]));
        s.setLabor_cost(parseDouble(fields[11]));
        //s.setPaid(parseDouble(fields[11])); // Dilersen ayrı sütunla güncellenebilir
        s.setPayment_type(clean(fields[12]));
        s.setService_status(clean(fields[13]));
        s.setAction_taken(clean(fields[15]));

        // warranty_date (dd/MM/yyyy)
        s.setWarranty_date(parseDate(fields[20], DATE_TIME_FORMATTER));

        // delivery_at (dd/MM/yyyy HH:mm:ss)
        s.setDelivery_at(parseDate(fields[22], DATE_TIME_FORMATTER));

        // created_at (dd/MM/yyyy HH:mm:ss)
        s.setCreated_at(parseDate(fields[21], DATE_TIME_FORMATTER));


        return s;
    }
}

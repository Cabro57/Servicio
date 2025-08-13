package tr.cabro.servicio.importer.mapper;

import tr.cabro.servicio.model.PaymentType;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.model.ServiceStatus;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class ServiceCsvMapper extends BaseCsvMapper<Service> {

    static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Map<String, String> SERVICE_STATUS_MAP = new HashMap<>();
    private static final Map<String, String> PAYMENT_TYPE_MAP = new HashMap<>();

    static {
        // ServiceStatus mapping
        SERVICE_STATUS_MAP.put("Teslim Edildi", "Teslim Edildi");
        SERVICE_STATUS_MAP.put("İptal İade", "İade");
        SERVICE_STATUS_MAP.put("Tamir Edildi", "Hazır");
        SERVICE_STATUS_MAP.put("Tamirde", "Tamirde");
        SERVICE_STATUS_MAP.put("", "Tamirde"); // boş değer

        // PaymentType mapping
        PAYMENT_TYPE_MAP.put("Nakit Ödeme", "Nakit");
        PAYMENT_TYPE_MAP.put("Cari Hesap", "Veresiye");
        PAYMENT_TYPE_MAP.put("Ödeme Yapılmadı", "Nakit");
        PAYMENT_TYPE_MAP.put("Banka Havalesi", "Banka Havale/EFT");
        PAYMENT_TYPE_MAP.put("", "Nakit"); // boş değer
    }

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

        // PaymentType dönüşümü
        String paymentRaw = clean(fields[12]);
        String paymentMapped = PAYMENT_TYPE_MAP.getOrDefault(paymentRaw, "Nakit");
        s.setPayment_type(PaymentType.of(paymentMapped));

        // ServiceStatus dönüşümü
        String statusRaw = clean(fields[13]);
        String statusMapped = SERVICE_STATUS_MAP.getOrDefault(statusRaw, "Tamirde");
        s.setService_status(ServiceStatus.of(statusMapped));

        s.setAction_taken(clean(fields[15]));

        // warranty_date (dd/MM/yyyy)
        s.setWarranty_date(parseDateTime(fields[20], DATE_TIME_FORMATTER));

        // delivery_at (dd/MM/yyyy HH:mm:ss)
        if (s.getService_status() == ServiceStatus.DELIVERED || s.getService_status() == ServiceStatus.RETURN) {
            s.setDelivery_at(parseDateTime(fields[22], DATE_TIME_FORMATTER));
        }

        // created_at (dd/MM/yyyy HH:mm:ss)
        s.setCreated_at(parseDateTime(fields[21], DATE_TIME_FORMATTER));

        return s;
    }
}
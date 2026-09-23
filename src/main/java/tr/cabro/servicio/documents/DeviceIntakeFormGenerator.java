package tr.cabro.servicio.documents;

import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.util.PhoneHelper;

import java.io.File;
import java.time.LocalDateTime;

/**
 * Cihaz Kabul Formu — müşteri cihazı servise bırakırken imzalanır. Bu aşamada henüz
 * ücretlendirme belli olmayabileceği için kalem/tutar tablosu içermez.
 * Termal karşılığı: {@link DeviceIntakeSlipGenerator}.
 */
public class DeviceIntakeFormGenerator implements ServiceFormGenerator {


    @Override
    public File generate(WorkOrder workOrder, User shop, DocumentRequest request, DocumentFormat format, File outFile) throws Exception {
        Customer customer = workOrder.getCustomer();
        Device device = workOrder.getDevice();

        DocumentWriter pdf = DocumentWriter.open(format, outFile, shop, "Cihaz Kabul Formu",
                "SRV-" + workOrder.getId(), date(workOrder.getCreatedAt()));

        pdf.section("Müşteri ve Cihaz");
        pdf.fields(new String[][]{
                {"Müşteri", customer != null ? customer.getFullName() : null},
                {"Telefon", DeviceIntakeFormGenerator.phone(customer)},
                {"Cihaz", device != null ? device.getDisplayName() : null},
                {"Seri No / IMEI", device != null ? device.getSerialNo() : null}
        });
        // Ekran kilidi (PIN/şifre/desen) ayrı tabloda şifreli tutulur ve bilinçli olarak basılmaz.
        pdf.wideField("Aksesuar", device != null ? device.getAccessory() : null);

        pdf.section("Bildirilen Arıza");
        pdf.paragraph(workOrder.getReportedFault(), "Belirtilmemiş.");

        pdf.section("Koşullar");
        pdf.terms(request.text(DocumentText.INTAKE_TERMS));

        pdf.signatures("Teslim Eden (Müşteri)", request.getLeftSignerName(), "Teslim Alan (İşletme)", request.getRightSignerName());
        pdf.close();
        return outFile;
    }

    /** Servis belgelerinin ortak tarih biçimi (bölge ayarına göre). */
    static String date(LocalDateTime value) {
        return value != null ? value.format(DateFormats.shortDate()) : "—";
    }

    static String phone(Customer c) {
        return c != null && c.getPhoneNumber1() != null ? PhoneHelper.formatForDisplay(c.getPhoneNumber1()) : null;
    }
}

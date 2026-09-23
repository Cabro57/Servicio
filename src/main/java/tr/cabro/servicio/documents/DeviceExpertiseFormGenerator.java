package tr.cabro.servicio.documents;

import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.DeviceTransaction;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.util.PhoneHelper;

import java.io.File;
import java.time.LocalDateTime;

/**
 * Cihaz Ekspertizi Formu — ikinci el alım sırasında cihazın durumu/fonksiyon testi notlarını
 * kayıt altına alan dahili bir belge. İmza gerektirmez.
 */
public class DeviceExpertiseFormGenerator implements DeviceTransactionFormGenerator {

    @Override
    public File generate(DeviceTransaction transaction, User shop, DocumentRequest request, DocumentFormat format, File outFile) throws Exception {
        Customer seller = transaction.getCustomer();
        Device device = transaction.getDevice();

        DocumentWriter pdf = DocumentWriter.open(format, outFile, shop, "Cihaz Ekspertizi Formu",
                "DT-" + transaction.getId(), date(transaction.getTransactionDate()));

        pdf.section("Satıcı ve Cihaz");
        pdf.fields(new String[][]{
                {"Satıcı", seller != null ? seller.getFullName() : null},
                {"Telefon", phone(seller)},
                {"Cihaz", device != null ? device.getDisplayName() : null},
                {"Seri No / IMEI", device != null ? device.getSerialNo() : null},
                {"Alım Fiyatı", PdfDocumentBuilder.money(transaction.getPrice())}
        });

        pdf.section("Ekspertiz Notları");
        pdf.paragraph(transaction.getExpertiseNotes(), "Belirtilmemiş.");

        pdf.close();
        return outFile;
    }

    /** 2.el belgelerinin ortak tarih biçimi (bölge ayarına göre). */
    static String date(LocalDateTime value) {
        return value != null ? value.format(DateFormats.shortDate()) : "—";
    }

    static String phone(Customer c) {
        return c != null && c.getPhoneNumber1() != null ? PhoneHelper.formatForDisplay(c.getPhoneNumber1()) : null;
    }
}

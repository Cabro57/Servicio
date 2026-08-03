package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.DeviceTransaction;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Cihaz Ekspertizi Formu — ikinci el alım sırasında cihazın durumu/fonksiyon testi notlarını
 * kayıt altına alan dahili bir belge. İmza gerektirmez.
 */
public class DeviceExpertiseFormGenerator implements DeviceTransactionFormGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy", new Locale("tr", "TR"));

    @Override
    public File generate(DeviceTransaction transaction, User shop, String leftSignerName, String rightSignerName) throws Exception {
        Customer seller = transaction.getCustomer();
        Device device = transaction.getDevice();

        File outFile = File.createTempFile("servicio-cihaz-ekspertiz-DT" + transaction.getId() + "-", ".pdf");
        outFile.deleteOnExit();

        PdfDocumentBuilder pdf = new PdfDocumentBuilder(outFile);
        pdf.addLetterhead(shop);
        pdf.addTitle("Cihaz Ekspertizi Formu");

        pdf.document.add(pdf.buildInfoTable(new String[][]{
                {"Kayıt No", "DT-" + transaction.getId()},
                {"Tarih", transaction.getTransactionDate() != null ? transaction.getTransactionDate().format(DATE_FORMATTER) : "-"},
                {"Satıcı", seller != null ? seller.getFullName() : "-"},
                {"Telefon", seller != null && seller.getPhoneNumber1() != null ? PhoneHelper.formatForDisplay(seller.getPhoneNumber1()) : "-"},
                {"Cihaz", device != null ? device.getDisplayName() : "-"},
                {"Seri No", device != null && device.getSerialNo() != null ? device.getSerialNo() : "-"},
                {"Alım Fiyatı", Format.formatPrice(transaction.getPrice())}
        }));
        pdf.addSpacer();

        pdf.document.add(new com.lowagie.text.Paragraph("Ekspertiz Notları", pdf.sectionFont));
        pdf.addParagraph(transaction.getExpertiseNotes() != null && !transaction.getExpertiseNotes().isBlank()
                ? transaction.getExpertiseNotes() : "Belirtilmemiş.");

        pdf.close();
        return outFile;
    }
}

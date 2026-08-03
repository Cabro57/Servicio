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
 * Satın Alma Sözleşmesi — işletmenin bir müşteriden ikinci el cihaz satın aldığını belgeler
 * (imzalı: işletme + satıcı müşteri).
 */
public class PurchaseContractFormGenerator implements DeviceTransactionFormGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy", new Locale("tr", "TR"));

    private static final String CONTRACT_NOTE = "Yukarıda bilgileri belirtilen cihaz, tarafımdan yukarıdaki bedel " +
            "karşılığında işletmeye satılmıştır. Cihazın yasal olarak tarafıma ait olduğunu ve üzerinde " +
            "üçüncü şahıslara ait herhangi bir hak/talep bulunmadığını beyan ederim.";

    @Override
    public File generate(DeviceTransaction transaction, User shop, String leftSignerName, String rightSignerName) throws Exception {
        Customer seller = transaction.getCustomer();
        Device device = transaction.getDevice();

        File outFile = File.createTempFile("servicio-satin-alma-sozlesmesi-DT" + transaction.getId() + "-", ".pdf");
        outFile.deleteOnExit();

        PdfDocumentBuilder pdf = new PdfDocumentBuilder(outFile);
        pdf.addLetterhead(shop);
        pdf.addTitle("Satın Alma Sözleşmesi");

        pdf.document.add(pdf.buildInfoTable(new String[][]{
                {"Kayıt No", "DT-" + transaction.getId()},
                {"Tarih", transaction.getTransactionDate() != null ? transaction.getTransactionDate().format(DATE_FORMATTER) : "-"},
                {"Satıcı", seller != null ? seller.getFullName() : "-"},
                {"Telefon", seller != null && seller.getPhoneNumber1() != null ? PhoneHelper.formatForDisplay(seller.getPhoneNumber1()) : "-"},
                {"Cihaz", device != null ? device.getDisplayName() : "-"},
                {"Seri No", device != null && device.getSerialNo() != null ? device.getSerialNo() : "-"},
                {"Alım Bedeli", Format.formatPrice(transaction.getPrice())}
        }));
        pdf.addSpacer();

        pdf.addParagraph(CONTRACT_NOTE);

        pdf.document.add(pdf.buildSignatureLines("İşletme", leftSignerName, "Satıcı (Müşteri)", rightSignerName));
        pdf.close();

        return outFile;
    }
}

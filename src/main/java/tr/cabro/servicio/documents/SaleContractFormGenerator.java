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
 * Satış Sözleşmesi — işletmenin stoktaki bir ikinci el cihazı bir müşteriye sattığını belgeler
 * (imzalı: işletme + alıcı müşteri).
 */
public class SaleContractFormGenerator implements DeviceTransactionFormGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy", new Locale("tr", "TR"));

    private static final String CONTRACT_NOTE = "Yukarıda bilgileri belirtilen ikinci el cihaz, tarafıma yukarıdaki bedel " +
            "karşılığında, mevcut/görülen haliyle satılmıştır. Cihazı bu şartlarla teslim aldığımı beyan ederim.";

    @Override
    public File generate(DeviceTransaction transaction, User shop, String leftSignerName, String rightSignerName) throws Exception {
        Customer buyer = transaction.getCustomer();
        Device device = transaction.getDevice();

        File outFile = File.createTempFile("servicio-satis-sozlesmesi-DT" + transaction.getId() + "-", ".pdf");
        outFile.deleteOnExit();

        PdfDocumentBuilder pdf = new PdfDocumentBuilder(outFile);
        pdf.addLetterhead(shop);
        pdf.addTitle("Satış Sözleşmesi");

        pdf.document.add(pdf.buildInfoTable(new String[][]{
                {"Kayıt No", "DT-" + transaction.getId()},
                {"Tarih", transaction.getTransactionDate() != null ? transaction.getTransactionDate().format(DATE_FORMATTER) : "-"},
                {"Alıcı", buyer != null ? buyer.getFullName() : "-"},
                {"Telefon", buyer != null && buyer.getPhoneNumber1() != null ? PhoneHelper.formatForDisplay(buyer.getPhoneNumber1()) : "-"},
                {"Cihaz", device != null ? device.getDisplayName() : "-"},
                {"Seri No", device != null && device.getSerialNo() != null ? device.getSerialNo() : "-"},
                {"Satış Bedeli", Format.formatPrice(transaction.getPrice())},
                {"Garanti Süresi", transaction.getWarrantyMonths() != null ? transaction.getWarrantyMonths() + " ay" : "Garantisiz"}
        }));
        pdf.addSpacer();

        pdf.addParagraph(CONTRACT_NOTE);

        pdf.document.add(pdf.buildSignatureLines("İşletme", leftSignerName, "Alıcı (Müşteri)", rightSignerName));
        pdf.close();

        return outFile;
    }
}

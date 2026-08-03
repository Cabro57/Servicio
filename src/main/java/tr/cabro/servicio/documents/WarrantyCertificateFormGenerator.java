package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.DeviceTransaction;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.util.Format;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Garanti Belgesi — satılan ikinci el cihaz için verilen garanti süresi/koşullarını belgeler.
 * İmza gerektirmez, yalnızca satışı yapılmış (SALE) işlemler için üretilir.
 */
public class WarrantyCertificateFormGenerator implements DeviceTransactionFormGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy", new Locale("tr", "TR"));

    private static final String WARRANTY_NOTE = "Bu garanti, cihazın normal kullanım koşullarında ortaya çıkan " +
            "donanımsal arızalarını kapsar; düşürme, sıvı teması, yetkisiz müdahale ve kullanıcı hatasından " +
            "kaynaklanan hasarları kapsamaz.";

    @Override
    public File generate(DeviceTransaction transaction, User shop, String leftSignerName, String rightSignerName) throws Exception {
        Customer buyer = transaction.getCustomer();
        Device device = transaction.getDevice();

        File outFile = File.createTempFile("servicio-garanti-belgesi-DT" + transaction.getId() + "-", ".pdf");
        outFile.deleteOnExit();

        PdfDocumentBuilder pdf = new PdfDocumentBuilder(outFile);
        pdf.addLetterhead(shop);
        pdf.addTitle("Garanti Belgesi");

        Integer months = transaction.getWarrantyMonths();
        String warrantyEnd = "-";
        if (months != null && transaction.getTransactionDate() != null) {
            warrantyEnd = transaction.getTransactionDate().plusMonths(months).format(DATE_FORMATTER);
        }

        pdf.document.add(pdf.buildInfoTable(new String[][]{
                {"Kayıt No", "DT-" + transaction.getId()},
                {"Satış Tarihi", transaction.getTransactionDate() != null ? transaction.getTransactionDate().format(DATE_FORMATTER) : "-"},
                {"Alıcı", buyer != null ? buyer.getFullName() : "-"},
                {"Cihaz", device != null ? device.getDisplayName() : "-"},
                {"Seri No", device != null && device.getSerialNo() != null ? device.getSerialNo() : "-"},
                {"Satış Bedeli", Format.formatPrice(transaction.getPrice())},
                {"Garanti Süresi", months != null ? months + " ay" : "Garantisiz"},
                {"Garanti Bitiş Tarihi", warrantyEnd}
        }));
        pdf.addSpacer();

        pdf.addParagraph(WARRANTY_NOTE);

        pdf.close();
        return outFile;
    }
}

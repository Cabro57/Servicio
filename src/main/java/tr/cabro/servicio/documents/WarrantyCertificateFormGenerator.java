package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.DeviceTransaction;
import tr.cabro.servicio.model.User;

import java.io.File;

/**
 * Garanti Belgesi — satılan ikinci el cihaz için verilen garanti süresi/koşullarını belgeler.
 * İmza gerektirmez, yalnızca satışı yapılmış (SALE) işlemler için üretilir.
 */
public class WarrantyCertificateFormGenerator implements DeviceTransactionFormGenerator {


    @Override
    public File generate(DeviceTransaction transaction, User shop, DocumentRequest request, DocumentFormat format, File outFile) throws Exception {
        Customer buyer = transaction.getCustomer();
        Device device = transaction.getDevice();

        Integer months = transaction.getWarrantyMonths();
        String warrantyEnd = months != null && transaction.getTransactionDate() != null
                ? DeviceExpertiseFormGenerator.date(transaction.getTransactionDate().plusMonths(months)) : "—";

        DocumentWriter pdf = DocumentWriter.open(format, outFile, shop, "Garanti Belgesi",
                "DT-" + transaction.getId(), DeviceExpertiseFormGenerator.date(transaction.getTransactionDate()));

        pdf.section("Garanti");
        pdf.fields(new String[][]{
                {"Garanti Süresi", months != null ? months + " ay" : "Garantisiz"},
                {"Garanti Bitiş Tarihi", warrantyEnd}
        });

        pdf.section("Alıcı ve Cihaz");
        pdf.fields(new String[][]{
                {"Alıcı", buyer != null ? buyer.getFullName() : null},
                {"Satış Tarihi", DeviceExpertiseFormGenerator.date(transaction.getTransactionDate())},
                {"Cihaz", device != null ? device.getDisplayName() : null},
                {"Seri No / IMEI", device != null ? device.getSerialNo() : null},
                {"Satış Bedeli", PdfDocumentBuilder.money(transaction.getPrice())}
        });

        pdf.section("Kapsam");
        pdf.terms(request.text(DocumentText.WARRANTY_CERTIFICATE_NOTE));

        pdf.close();
        return outFile;
    }
}

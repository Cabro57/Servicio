package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.DeviceTransaction;
import tr.cabro.servicio.model.User;

import java.io.File;

/**
 * Satın Alma Sözleşmesi — işletmenin bir müşteriden ikinci el cihaz satın aldığını belgeler
 * (imzalı: işletme + satıcı müşteri).
 */
public class PurchaseContractFormGenerator implements DeviceTransactionFormGenerator {


    @Override
    public File generate(DeviceTransaction transaction, User shop, DocumentRequest request, DocumentFormat format, File outFile) throws Exception {
        Customer party = transaction.getCustomer();
        Device device = transaction.getDevice();

        DocumentWriter pdf = DocumentWriter.open(format, outFile, shop, "Satın Alma Sözleşmesi",
                "DT-" + transaction.getId(), DeviceExpertiseFormGenerator.date(transaction.getTransactionDate()));

        pdf.section("Satıcı ve Cihaz");
        pdf.fields(new String[][]{
                {"Satıcı", party != null ? party.getFullName() : null},
                {"Telefon", DeviceExpertiseFormGenerator.phone(party)},
                {"Cihaz", device != null ? device.getDisplayName() : null},
                {"Seri No / IMEI", device != null ? device.getSerialNo() : null},
                {"Alım Bedeli", PdfDocumentBuilder.money(transaction.getPrice())}
        });

        pdf.section("Beyan");
        pdf.terms(request.text(DocumentText.PURCHASE_CONTRACT_NOTE));

        pdf.signatures("İşletme", request.getLeftSignerName(), "Satıcı (Müşteri)", request.getRightSignerName());
        pdf.close();
        return outFile;
    }
}

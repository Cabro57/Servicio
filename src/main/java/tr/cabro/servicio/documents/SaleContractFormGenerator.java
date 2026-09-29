package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.DeviceTransaction;
import tr.cabro.servicio.model.Business;
import tr.cabro.servicio.model.User;

import java.io.File;

/**
 * Satış Sözleşmesi — işletmenin stoktaki bir ikinci el cihazı bir müşteriye sattığını belgeler
 * (imzalı: işletme + alıcı müşteri).
 */
public class SaleContractFormGenerator implements DeviceTransactionFormGenerator {


    @Override
    public File generate(DeviceTransaction transaction, Business shop, DocumentRequest request, DocumentFormat format, File outFile) throws Exception {
        Customer party = transaction.getCustomer();
        Device device = transaction.getDevice();

        DocumentWriter pdf = DocumentWriter.open(format, outFile, shop, "Satış Sözleşmesi",
                "DT-" + transaction.getId(), DeviceExpertiseFormGenerator.date(transaction.getTransactionDate()));
        try {
            pdf.section("Alıcı ve Cihaz");
            pdf.fields(new String[][]{
                    {"Alıcı", party != null ? party.getFullName() : null},
                    {"Telefon", DeviceExpertiseFormGenerator.phone(party)},
                    {"Cihaz", device != null ? device.getDisplayName() : null},
                    {"Seri No / IMEI", device != null ? device.getSerialNo() : null},
                    {"Satış Bedeli", PdfDocumentBuilder.money(transaction.getPrice())},
                    {"Garanti Süresi", transaction.getWarrantyMonths() != null ? transaction.getWarrantyMonths() + " ay" : "Garantisiz"}
            });
            pdf.paymentInstructions(shop, "DT-" + transaction.getId(), transaction.getPrice());

            pdf.section("Beyan");
            pdf.terms(request.text(DocumentText.SALE_CONTRACT_NOTE));

            pdf.signatures("İşletme", request.getLeftSignerName(), "Alıcı (Müşteri)", request.getRightSignerName());
            pdf.close();
        } catch (Exception e) {
            pdf.abort();
            throw e;
        }
        return outFile;
    }
}

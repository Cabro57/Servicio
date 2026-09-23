package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrder;

import java.io.File;
import java.time.LocalDateTime;

/**
 * Teklif / Onarım Onayı Formu — tespit edilen arıza ve önerilen işlem/parça ücretlendirmesi
 * müşteriye sunulur; müşteri bu tutarla onarımın yapılmasını onaylar veya reddeder (imzalı).
 */
public class RepairQuoteApprovalFormGenerator implements ServiceFormGenerator {


    @Override
    public File generate(WorkOrder workOrder, User shop, DocumentRequest request, DocumentFormat format, File outFile) throws Exception {
        Customer customer = workOrder.getCustomer();
        Device device = workOrder.getDevice();

        DocumentWriter pdf = DocumentWriter.open(format, outFile, shop, "Teklif ve Onarım Onay Formu",
                "SRV-" + workOrder.getId(), DeviceIntakeFormGenerator.date(LocalDateTime.now()));

        pdf.section("Müşteri ve Cihaz");
        pdf.fields(new String[][]{
                {"Müşteri", customer != null ? customer.getFullName() : null},
                {"Telefon", DeviceIntakeFormGenerator.phone(customer)},
                {"Cihaz", device != null ? device.getDisplayName() : null},
                {"Seri No / IMEI", device != null ? device.getSerialNo() : null}
        });

        pdf.section("Tespit Edilen Arıza");
        String detected = PdfDocumentBuilder.notBlank(workOrder.getDetectedFault())
                ? workOrder.getDetectedFault() : workOrder.getReportedFault();
        pdf.paragraph(detected, "Belirtilmemiş.");

        pdf.section("Önerilen İşlemler ve Ücretlendirme");
        pdf.items(workOrder.getItems(), workOrder.getTotalServiceAmount());

        pdf.section("Müşteri Onayı");
        pdf.terms(request.text(DocumentText.QUOTE_APPROVAL_NOTE));
        pdf.choices("Onaylıyorum", "Onaylamıyorum");

        pdf.signatures("Teklifi Sunan (İşletme)", request.getLeftSignerName(), "Onaylayan (Müşteri)", request.getRightSignerName());
        pdf.close();
        return outFile;
    }
}

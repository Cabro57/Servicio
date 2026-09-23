package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrder;

import java.io.File;

/**
 * Arıza Tespit Formu — teknisyenin cihazı incelemesi sonrası müşterinin bildirdiği arıza ile
 * tespit ettiği asıl arızayı yan yana kaydeden dahili bir belge. İmza gerektirmez.
 */
public class FaultDiagnosisFormGenerator implements ServiceFormGenerator {

    @Override
    public File generate(WorkOrder workOrder, User shop, DocumentRequest request, DocumentFormat format, File outFile) throws Exception {
        Customer customer = workOrder.getCustomer();
        Device device = workOrder.getDevice();

        DocumentWriter pdf = DocumentWriter.open(format, outFile, shop, "Arıza Tespit Formu",
                "SRV-" + workOrder.getId(), DeviceIntakeFormGenerator.date(workOrder.getCreatedAt()));

        pdf.section("Müşteri ve Cihaz");
        pdf.fields(new String[][]{
                {"Müşteri", customer != null ? customer.getFullName() : null},
                {"Telefon", DeviceIntakeFormGenerator.phone(customer)},
                {"Cihaz", device != null ? device.getDisplayName() : null},
                {"Seri No / IMEI", device != null ? device.getSerialNo() : null}
        });

        pdf.section("Bildirilen Arıza (Müşteri)");
        pdf.paragraph(workOrder.getReportedFault(), "Belirtilmemiş.");

        pdf.section("Tespit Edilen Arıza (Teknisyen)");
        pdf.paragraph(workOrder.getDetectedFault(), "Henüz belirlenmedi.");

        pdf.close();
        return outFile;
    }
}

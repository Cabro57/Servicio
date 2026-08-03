package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.util.PhoneHelper;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Arıza Tespit Formu — teknisyenin cihazı incelemesi sonrası müşterinin bildirdiği arıza ile
 * tespit ettiği asıl arızayı yan yana kaydeden dahili bir belge. İmza gerektirmez.
 */
public class FaultDiagnosisFormGenerator implements ServiceFormGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy", new Locale("tr", "TR"));

    @Override
    public File generate(WorkOrder workOrder, User shop, String leftSignerName, String rightSignerName) throws Exception {
        Customer customer = workOrder.getCustomer();
        Device device = workOrder.getDevice();

        File outFile = File.createTempFile("servicio-ariza-tespit-SRV" + workOrder.getId() + "-", ".pdf");
        outFile.deleteOnExit();

        PdfDocumentBuilder pdf = new PdfDocumentBuilder(outFile);
        pdf.addLetterhead(shop);
        pdf.addTitle("Arıza Tespit Formu");

        pdf.document.add(pdf.buildInfoTable(new String[][]{
                {"Kayıt No", "SRV-" + workOrder.getId()},
                {"Tarih", workOrder.getCreatedAt() != null ? workOrder.getCreatedAt().format(DATE_FORMATTER) : "-"},
                {"Müşteri", customer != null ? customer.getFullName() : "-"},
                {"Telefon", customer != null && customer.getPhoneNumber1() != null ? PhoneHelper.formatForDisplay(customer.getPhoneNumber1()) : "-"},
                {"Cihaz", device != null ? device.getDisplayName() : "-"},
                {"Seri No", device != null && device.getSerialNo() != null ? device.getSerialNo() : "-"}
        }));
        pdf.addSpacer();

        pdf.document.add(new com.lowagie.text.Paragraph("Bildirilen Arıza (Müşteri)", pdf.sectionFont));
        pdf.addParagraph(workOrder.getReportedFault() != null && !workOrder.getReportedFault().isBlank()
                ? workOrder.getReportedFault() : "Belirtilmemiş.");
        pdf.addSpacer();

        pdf.document.add(new com.lowagie.text.Paragraph("Tespit Edilen Arıza (Teknisyen)", pdf.sectionFont));
        pdf.addParagraph(workOrder.getDetectedFault() != null && !workOrder.getDetectedFault().isBlank()
                ? workOrder.getDetectedFault() : "Henüz belirlenmedi.");

        pdf.close();
        return outFile;
    }
}

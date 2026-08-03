package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.util.PhoneHelper;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Locale;

/**
 * Teklif / Onarım Onayı Formu — tespit edilen arıza ve önerilen işlem/parça ücretlendirmesi
 * müşteriye sunulur; müşteri bu tutarla onarımın yapılmasını onaylar veya reddeder (imzalı).
 */
public class RepairQuoteApprovalFormGenerator implements ServiceFormGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy", new Locale("tr", "TR"));

    private static final String APPROVAL_NOTE = "Yukarıda belirtilen tespit ve önerilen işlemler/tutar tarafıma bildirilmiştir. " +
            "Onarımın bu şartlarla yapılmasını onaylıyorum.";

    @Override
    public File generate(WorkOrder workOrder, User shop, String leftSignerName, String rightSignerName) throws Exception {
        Customer customer = workOrder.getCustomer();
        Device device = workOrder.getDevice();

        File outFile = File.createTempFile("servicio-teklif-onay-SRV" + workOrder.getId() + "-", ".pdf");
        outFile.deleteOnExit();

        PdfDocumentBuilder pdf = new PdfDocumentBuilder(outFile);
        pdf.addLetterhead(shop);
        pdf.addTitle("Teklif ve Onarım Onay Formu");

        pdf.document.add(pdf.buildInfoTable(new String[][]{
                {"Kayıt No", "SRV-" + workOrder.getId()},
                {"Tarih", java.time.LocalDateTime.now().format(DATE_FORMATTER)},
                {"Müşteri", customer != null ? customer.getFullName() : "-"},
                {"Telefon", customer != null && customer.getPhoneNumber1() != null ? PhoneHelper.formatForDisplay(customer.getPhoneNumber1()) : "-"},
                {"Cihaz", device != null ? device.getDisplayName() : "-"},
                {"Seri No", device != null && device.getSerialNo() != null ? device.getSerialNo() : "-"}
        }));
        pdf.addSpacer();

        pdf.document.add(new com.lowagie.text.Paragraph("Tespit Edilen Arıza", pdf.sectionFont));
        String detected = workOrder.getDetectedFault() != null && !workOrder.getDetectedFault().isBlank()
                ? workOrder.getDetectedFault()
                : (workOrder.getReportedFault() != null && !workOrder.getReportedFault().isBlank()
                        ? workOrder.getReportedFault() : "Belirtilmemiş.");
        pdf.addParagraph(detected);
        pdf.addSpacer();

        pdf.document.add(new com.lowagie.text.Paragraph("Önerilen İşlemler ve Ücretlendirme", pdf.sectionFont));
        pdf.addSpacer();
        pdf.document.add(pdf.buildItemsTable(
                workOrder.getItems() != null ? workOrder.getItems() : Collections.emptyList(),
                workOrder.getTotalServiceAmount()));
        pdf.addSpacer();

        pdf.addParagraph(APPROVAL_NOTE);
        pdf.addSpacer();
        pdf.addParagraph("☐ Onaylıyorum        ☐ Onaylamıyorum");

        pdf.document.add(pdf.buildSignatureLines("Teklifi Sunan (İşletme)", leftSignerName, "Onaylayan (Müşteri)", rightSignerName));
        pdf.close();

        return outFile;
    }
}

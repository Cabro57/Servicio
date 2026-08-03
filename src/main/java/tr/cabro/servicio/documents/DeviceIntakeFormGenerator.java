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
 * Cihaz Kabul Formu — müşteri cihazı servise bırakırken imzalanır. Bu aşamada henüz
 * ücretlendirme belli olmayabileceği için kalem/tutar tablosu içermez.
 */
public class DeviceIntakeFormGenerator implements ServiceFormGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy", new Locale("tr", "TR"));

    private static final String TERMS = "Cihaz, belirtilen arıza şikayeti ile teslim alınmıştır. Onarım öncesi cihaz üzerinde " +
            "yedeklenmemiş verilerin kaybolabileceğini, tahmini onarım süresinin arızanın niteliğine göre değişebileceğini " +
            "müşteri kabul eder.";

    @Override
    public File generate(WorkOrder workOrder, User shop, String leftSignerName, String rightSignerName) throws Exception {
        Customer customer = workOrder.getCustomer();
        Device device = workOrder.getDevice();

        File outFile = File.createTempFile("servicio-cihaz-kabul-SRV" + workOrder.getId() + "-", ".pdf");
        outFile.deleteOnExit();

        PdfDocumentBuilder pdf = new PdfDocumentBuilder(outFile);
        pdf.addLetterhead(shop);
        pdf.addTitle("Cihaz Kabul Formu");

        pdf.document.add(pdf.buildInfoTable(new String[][]{
                {"Kayıt No", "SRV-" + workOrder.getId()},
                {"Tarih", workOrder.getCreatedAt() != null ? workOrder.getCreatedAt().format(DATE_FORMATTER) : "-"},
                {"Müşteri", customer != null ? customer.getFullName() : "-"},
                {"Telefon", customer != null && customer.getPhoneNumber1() != null ? PhoneHelper.formatForDisplay(customer.getPhoneNumber1()) : "-"},
                {"Cihaz", device != null ? device.getDisplayName() : "-"},
                {"Seri No", device != null && device.getSerialNo() != null ? device.getSerialNo() : "-"},
                {"Aksesuar", device != null && device.getAccessory() != null && !device.getAccessory().isBlank() ? device.getAccessory() : "-"}
                // NOT: Ekran kilidi (PIN/şifre/desen) artık Device'ta değil, ayrı bir tabloda
                // (DeviceAccessCredential) tutuluyor — bu PDF formuna eklenmesi ayrı bir işte ele alınacak.
        }));
        pdf.addSpacer();

        pdf.document.add(new com.lowagie.text.Paragraph("Bildirilen Arıza", pdf.sectionFont));
        pdf.addParagraph(workOrder.getReportedFault() != null && !workOrder.getReportedFault().isBlank()
                ? workOrder.getReportedFault() : "Belirtilmemiş.");
        pdf.addSpacer();

        pdf.document.add(new com.lowagie.text.Paragraph("Koşullar", pdf.sectionFont));
        pdf.addParagraph(TERMS);
        pdf.addSpacer();

        pdf.document.add(pdf.buildSignatureLines("Teslim Eden (Müşteri)", leftSignerName, "Teslim Alan (İşletme)", rightSignerName));
        pdf.close();

        return outFile;
    }
}

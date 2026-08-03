package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Locale;

/**
 * Servis Teslim Formu — onarım tamamlanıp cihaz müşteriye teslim edilirken imzalanır.
 * Yapılan işlemleri/parçaları ve ödeme özetini içerir.
 */
public class ServiceDeliveryFormGenerator implements ServiceFormGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy", new Locale("tr", "TR"));

    private static final String WARRANTY_NOTE = "Değiştirilen parçalar ve yapılan işçilik, üretici/tedarikçi garanti koşulları " +
            "saklı kalmak kaydıyla 90 gün garantilidir. Fiziksel darbe, sıvı teması veya yetkisiz müdahale garanti dışıdır.";

    @Override
    public File generate(WorkOrder workOrder, User shop, String leftSignerName, String rightSignerName) throws Exception {
        Customer customer = workOrder.getCustomer();
        Device device = workOrder.getDevice();

        File outFile = File.createTempFile("servicio-servis-teslim-SRV" + workOrder.getId() + "-", ".pdf");
        outFile.deleteOnExit();

        PdfDocumentBuilder pdf = new PdfDocumentBuilder(outFile);
        pdf.addLetterhead(shop);
        pdf.addTitle("Servis Teslim Formu");

        pdf.document.add(pdf.buildInfoTable(new String[][]{
                {"Kayıt No", "SRV-" + workOrder.getId()},
                {"Tarih", java.time.LocalDateTime.now().format(DATE_FORMATTER)},
                {"Müşteri", customer != null ? customer.getFullName() : "-"},
                {"Telefon", customer != null && customer.getPhoneNumber1() != null ? PhoneHelper.formatForDisplay(customer.getPhoneNumber1()) : "-"},
                {"Cihaz", device != null ? device.getDisplayName() : "-"},
                {"Seri No", device != null && device.getSerialNo() != null ? device.getSerialNo() : "-"}
        }));
        pdf.addSpacer();

        pdf.document.add(new com.lowagie.text.Paragraph("Yapılan İşlemler", pdf.sectionFont));
        pdf.addSpacer();
        pdf.document.add(pdf.buildItemsTable(
                workOrder.getItems() != null ? workOrder.getItems() : Collections.emptyList(),
                workOrder.getTotalServiceAmount()));
        pdf.addSpacer();

        pdf.document.add(pdf.buildInfoTable(new String[][]{
                {"Toplam Tutar", Format.formatPrice(workOrder.getTotalServiceAmount())}
        }));
        pdf.addSpacer();

        pdf.document.add(new com.lowagie.text.Paragraph("Garanti Notu", pdf.sectionFont));
        pdf.addParagraph(WARRANTY_NOTE);
        pdf.addSpacer();

        pdf.document.add(pdf.buildSignatureLines("Teslim Eden (İşletme)", leftSignerName, "Teslim Alan (Müşteri)", rightSignerName));
        pdf.close();

        return outFile;
    }
}

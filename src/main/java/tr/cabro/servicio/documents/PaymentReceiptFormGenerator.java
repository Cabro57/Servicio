package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.WorkOrderPayment;
import tr.cabro.servicio.util.Format;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Tahsilat Fişi — tek bir {@link WorkOrderPayment} kaydı için makbuz. Bir {@code WorkOrder}'a
 * değil tek bir ödemeye bağlı olduğu için {@link ServiceFormGenerator}'ı uygulamıyor, "Form"
 * popup menüsünde de listelenmiyor — ödeme tablosundaki "Fiş" butonundan üretiliyor
 * (bkz. {@code application.panels.workorder.WorkOrderPaymentsPanel}). İmza içermez.
 */
public class PaymentReceiptFormGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", new Locale("tr", "TR"));

    public File generate(WorkOrder workOrder, WorkOrderPayment payment, User shop) throws Exception {
        Customer customer = workOrder.getCustomer();

        File outFile = File.createTempFile("servicio-tahsilat-fis-SRV" + workOrder.getId() + "-", ".pdf");
        outFile.deleteOnExit();

        PdfDocumentBuilder pdf = new PdfDocumentBuilder(outFile);
        pdf.addLetterhead(shop);
        pdf.addTitle("Tahsilat Fişi");

        pdf.document.add(pdf.buildInfoTable(new String[][]{
                {"Kayıt No", "SRV-" + workOrder.getId()},
                {"Tarih", payment.getPaymentDate() != null ? payment.getPaymentDate().format(DATE_FORMATTER) : "-"},
                {"Müşteri", customer != null ? customer.getFullName() : "-"},
                {"Ödeme Yöntemi", payment.getPaymentType() != null ? payment.getPaymentType().getDisplayName() : "-"},
                {"Tutar", Format.formatPrice(payment.getAmount())}
        }));

        pdf.close();
        return outFile;
    }
}

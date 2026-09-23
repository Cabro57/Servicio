package tr.cabro.servicio.documents;

import tr.cabro.servicio.documents.receipt.ThermalSlip;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Payment;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrder;

import java.io.File;
import java.math.BigDecimal;

/**
 * Tahsilat Fişi — tek bir {@link Payment} kaydı için termal makbuz. Bir {@code WorkOrder}'a
 * değil tek bir ödemeye bağlı olduğu için {@link ServiceFormGenerator}'ı uygulamıyor, "Form"
 * popup menüsünde de listelenmiyor — ödeme tablosundaki "Fiş" butonundan üretiliyor
 * (bkz. {@code application.panels.workorder.WorkOrderPaymentsPanel}).
 * <p>
 * Tahsil edilen tutarın yanında servisin toplamı, ödenen ve kalan bakiye de basılır;
 * ödeme ekranındaki özetle aynı hesaptır ({@link WorkOrder#getRemainingAmount()}).
 */
public class PaymentReceiptFormGenerator {

    public File generate(WorkOrder workOrder, Payment payment, User shop) throws Exception {
        Customer customer = workOrder.getCustomer();
        File outFile = PdfDocumentBuilder.tempFile("tahsilat-fis-SRV" + workOrder.getId());

        ThermalSlip slip = ThermalSlip.create()
                .shopHeader(shop)
                .titleBand("Tahsilat Fişi")
                .documentLine("SRV-" + workOrder.getId(), payment.getPaymentDate())
                .field("Müşteri", customer != null ? customer.getFullName() : null)
                .field("Cihaz", workOrder.getDevice() != null ? workOrder.getDevice().getDisplayName() : null)
                .dashed()
                .field("Ödeme", payment.getPaymentType() != null ? payment.getPaymentType().getDisplayName() : null)
                .grandTotal("Tahsil Edilen", payment.getAmount())
                .dashed()
                .amount("Servis Toplamı", workOrder.getTotalServiceAmount())
                .amount("Toplam Ödenen", workOrder.getTotalPaid());

        BigDecimal remaining = workOrder.getRemainingAmount();
        slip.strongAmount(remaining.signum() < 0 ? "Fazla Ödeme" : "Kalan Bakiye", remaining.abs());

        slip.dashed().note(DocumentTexts.get(DocumentText.SLIP_FOOTER));
        return slip.write(outFile);
    }
}

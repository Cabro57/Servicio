package tr.cabro.servicio.documents;

import tr.cabro.servicio.documents.receipt.ThermalSlip;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.WorkOrderItem;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Servis Teslim Fişi — A4 teslim formunun termal karşılığı. Yapılan işlemler, toplam, ödenen
 * ve kalan bakiye ile garanti notunu içerir; müşteri cihazı teslim alırken imzalar.
 * Yalnızca PDF üretir ({@code format} yok sayılır).
 */
public class ServiceDeliverySlipGenerator implements ServiceFormGenerator {

    @Override
    public File generate(WorkOrder workOrder, User shop, DocumentRequest request, DocumentFormat format, File outFile) throws Exception {
        Customer customer = workOrder.getCustomer();
        Device device = workOrder.getDevice();
        String code = "SRV-" + workOrder.getId();

        ThermalSlip slip = ThermalSlip.create()
                .shopHeader(shop)
                .titleBand("Servis Teslim Fişi")
                .documentLine(code, LocalDateTime.now())
                .field("Müşteri", customer != null ? customer.getFullName() : null)
                .field("Cihaz", device != null ? device.getDisplayName() : null)
                .field("Seri No", device != null ? device.getSerialNo() : null)
                .dashed()
                .heading("Yapılan İşlemler");

        if (workOrder.getItems() == null || workOrder.getItems().isEmpty()) {
            slip.text("Kalem eklenmemiş.");
        } else {
            for (WorkOrderItem item : workOrder.getItems()) {
                slip.item(item.getItemName(), item.getQuantity() != null ? item.getQuantity() : 1,
                        item.getUnitPrice(), item.getTotalPrice());
            }
        }

        slip.grandTotal("Toplam", workOrder.getTotalServiceAmount())
                .amount("Ödenen", workOrder.getTotalPaid());
        BigDecimal remaining = workOrder.getRemainingAmount();
        slip.strongAmount(remaining.signum() < 0 ? "Fazla Ödeme" : "Kalan Bakiye", remaining.abs())
                .dashed()
                .heading("Garanti")
                .text(request.deliveryWarrantyNote())
                .signature("Teslim Alan (Müşteri)", customer != null ? customer.getFullName() : null)
                .dashed()
                .note(request.text(DocumentText.SLIP_FOOTER));

        return slip.write(outFile);
    }
}

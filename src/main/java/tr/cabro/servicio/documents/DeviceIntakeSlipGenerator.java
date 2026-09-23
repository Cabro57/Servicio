package tr.cabro.servicio.documents;

import tr.cabro.servicio.documents.receipt.ThermalSlip;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.util.PhoneHelper;

import java.io.File;

/**
 * Cihaz Kabul Fişi — A4 kabul formunun termal karşılığı. Cihazı bırakan müşteriye verilir;
 * en belirgin öğesi büyük takip numarasıdır, müşteri cihazını sorarken bu numarayı söyler.
 * Müşteri adı imza çizgisinin altına kendiliğinden basılır. Yalnızca PDF üretir ({@code format} yok sayılır).
 */
public class DeviceIntakeSlipGenerator implements ServiceFormGenerator {

    @Override
    public File generate(WorkOrder workOrder, User shop, DocumentRequest request, DocumentFormat format, File outFile) throws Exception {
        Customer customer = workOrder.getCustomer();
        Device device = workOrder.getDevice();
        String code = "SRV-" + workOrder.getId();

        ThermalSlip slip = ThermalSlip.create()
                .shopHeader(shop)
                .titleBand("Cihaz Kabul Fişi")
                .documentLine(code, workOrder.getCreatedAt())
                .bigCode("Takip No", code)
                .field("Müşteri", customer != null ? customer.getFullName() : null)
                .field("Telefon", customer != null && customer.getPhoneNumber1() != null
                        ? PhoneHelper.formatForDisplay(customer.getPhoneNumber1()) : null)
                .field("Cihaz", device != null ? device.getDisplayName() : null)
                .field("Seri No", device != null ? device.getSerialNo() : null)
                .field("Aksesuar", device != null ? device.getAccessory() : null)
                .heading("Bildirilen Arıza")
                .text(PdfDocumentBuilder.notBlank(workOrder.getReportedFault()) ? workOrder.getReportedFault() : "Belirtilmemiş.")
                .dashed()
                .heading("Koşullar")
                .text(request.text(DocumentText.INTAKE_TERMS))
                .signature("Teslim Eden (Müşteri)", customer != null ? customer.getFullName() : null)
                .dashed()
                .note(request.text(DocumentText.INTAKE_SLIP_NOTE));

        return slip.write(outFile);
    }
}

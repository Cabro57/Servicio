package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrder;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Servis Teslim Formu — onarım tamamlanıp cihaz müşteriye teslim edilirken imzalanır.
 * Yapılan işlemleri/parçaları ve ödeme özetini içerir.
 * Termal karşılığı: {@link ServiceDeliverySlipGenerator}.
 */
public class ServiceDeliveryFormGenerator implements ServiceFormGenerator {


    @Override
    public File generate(WorkOrder workOrder, User shop, DocumentRequest request, DocumentFormat format, File outFile) throws Exception {
        Customer customer = workOrder.getCustomer();
        Device device = workOrder.getDevice();

        DocumentWriter pdf = DocumentWriter.open(format, outFile, shop, "Servis Teslim Formu",
                "SRV-" + workOrder.getId(), DeviceIntakeFormGenerator.date(LocalDateTime.now()));

        pdf.section("Müşteri ve Cihaz");
        pdf.fields(new String[][]{
                {"Müşteri", customer != null ? customer.getFullName() : null},
                {"Telefon", DeviceIntakeFormGenerator.phone(customer)},
                {"Cihaz", device != null ? device.getDisplayName() : null},
                {"Seri No / IMEI", device != null ? device.getSerialNo() : null}
        });

        pdf.section("Yapılan İşlemler");
        pdf.items(workOrder.getItems(), workOrder.getTotalServiceAmount());

        BigDecimal remaining = workOrder.getRemainingAmount();
        pdf.fields(new String[][]{
                {"Ödenen", PdfDocumentBuilder.money(workOrder.getTotalPaid())},
                {remaining.signum() < 0 ? "Fazla Ödeme" : "Kalan Bakiye", PdfDocumentBuilder.money(remaining.abs())}
        });

        pdf.section("Garanti");
        pdf.terms(request.deliveryWarrantyNote());

        pdf.signatures("Teslim Eden (İşletme)", request.getLeftSignerName(), "Teslim Alan (Müşteri)", request.getRightSignerName());
        pdf.close();
        return outFile;
    }
}

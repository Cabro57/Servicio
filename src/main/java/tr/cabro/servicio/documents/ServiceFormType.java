package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrder;

import java.io.File;

/** "Form" popup menüsünde listelenen, kod tarafında hazır tasarlanmış sabit form türleri. */
public enum ServiceFormType {

    DEVICE_INTAKE("Cihaz Kabul Formu", new DeviceIntakeFormGenerator(),
            true, "Teslim Eden Adı (Müşteri)", "Teslim Alan Adı (İşletme)"),
    SERVICE_DELIVERY("Servis Teslim Formu", new ServiceDeliveryFormGenerator(),
            true, "Teslim Eden Adı (İşletme)", "Teslim Alan Adı (Müşteri)"),
    FAULT_DIAGNOSIS("Arıza Tespit Formu", new FaultDiagnosisFormGenerator(),
            false, null, null),
    REPAIR_QUOTE_APPROVAL("Teklif / Onarım Onayı Formu", new RepairQuoteApprovalFormGenerator(),
            true, "Teklifi Sunan Adı (İşletme)", "Onaylayan Adı (Müşteri)");

    private final String displayName;
    private final ServiceFormGenerator generator;
    private final boolean requiresSigners;
    private final String leftSignerLabel;
    private final String rightSignerLabel;

    ServiceFormType(String displayName, ServiceFormGenerator generator,
                    boolean requiresSigners, String leftSignerLabel, String rightSignerLabel) {
        this.displayName = displayName;
        this.generator = generator;
        this.requiresSigners = requiresSigners;
        this.leftSignerLabel = leftSignerLabel;
        this.rightSignerLabel = rightSignerLabel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean requiresSigners() {
        return requiresSigners;
    }

    public String getLeftSignerLabel() {
        return leftSignerLabel;
    }

    public String getRightSignerLabel() {
        return rightSignerLabel;
    }

    public File generate(WorkOrder workOrder, User shop, String leftSignerName, String rightSignerName) throws Exception {
        return generator.generate(workOrder, shop, leftSignerName, rightSignerName);
    }

    @Override
    public String toString() {
        return displayName;
    }
}

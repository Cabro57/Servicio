package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrder;

import java.io.File;

/** "Belge Oluştur" popup'ında listelenen, kod tarafında hazır tasarlanmış sabit form türleri. */
public enum ServiceFormType {

    DEVICE_INTAKE("Cihaz Kabul Formu", new DeviceIntakeFormGenerator()),
    SERVICE_DELIVERY("Servis Teslim Formu", new ServiceDeliveryFormGenerator());

    private final String displayName;
    private final ServiceFormGenerator generator;

    ServiceFormType(String displayName, ServiceFormGenerator generator) {
        this.displayName = displayName;
        this.generator = generator;
    }

    public String getDisplayName() {
        return displayName;
    }

    public File generate(WorkOrder workOrder, User shop) throws Exception {
        return generator.generate(workOrder, shop);
    }

    @Override
    public String toString() {
        return displayName;
    }
}

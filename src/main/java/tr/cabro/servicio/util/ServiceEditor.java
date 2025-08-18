package tr.cabro.servicio.util;

import lombok.Getter;
import lombok.Setter;
import tr.cabro.servicio.application.panels.*;
import tr.cabro.servicio.model.Service;

@Getter @Setter
public class ServiceEditor {

    private final Service service;

    private final CustomerInfoPanel customerPanel;
    private final DeviceInfoPanel devicePanel;
    private final FaultProcessInfoPanel faultPanel;
    private final PriceInfoPanel pricePanel;
    private final WarrantyInfoPanel warrantyPanel;
    private final PartsNotesInfoPanel partsPanel;
    private final StatusInfoPanel statusPanel;

    public ServiceEditor(Service service,
                         CustomerInfoPanel customerPanel,
                         DeviceInfoPanel devicePanel,
                         FaultProcessInfoPanel faultPanel,
                         PriceInfoPanel pricePanel,
                         WarrantyInfoPanel warrantyPanel,
                         PartsNotesInfoPanel partsPanel,
                         StatusInfoPanel statusPanel) {
        this.service = service;
        this.customerPanel = customerPanel;
        this.devicePanel = devicePanel;
        this.faultPanel = faultPanel;
        this.pricePanel = pricePanel;
        this.warrantyPanel = warrantyPanel;
        this.partsPanel = partsPanel;
        this.statusPanel = statusPanel;
    }
}

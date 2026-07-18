package tr.cabro.servicio.application.handlers;

import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.panels.setting.SettingsDevicePanel;
import tr.cabro.servicio.model.dictionary.DeviceBrand;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.service.DeviceDictionaryManager;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

public class TypeImportHandler extends TransferHandler {
    private final SettingsDevicePanel panel;
    private final DeviceDictionaryManager deviceDictService;

    public TypeImportHandler(SettingsDevicePanel panel) {
        this.panel = panel;
        this.deviceDictService = ServiceManager.getDeviceDictionaryManager();
    }

    @Override
    public boolean canImport(TransferSupport support) {
        return support.isDataFlavorSupported(DataFlavor.stringFlavor);
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) return false;

        try {
            String brandName = (String) support.getTransferable()
                    .getTransferData(DataFlavor.stringFlavor);

            JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
            int index = dl.getIndex();
            DeviceType targetType = panel.typeList.getModel().getElementAt(index);

            DeviceType sourceType = panel.typeList.getSelectedValue();

            DeviceBrand brand = panel.brandList.getSelectedValue();
            if (brand == null || !brand.getName().equals(brandName)) {
                return false;
            }

            if (sourceType != null && targetType != null && !sourceType.equals(targetType)) {
                deviceDictService.getBrandsByTypeId(targetType.getId()).thenAccept(targetBrands -> {
                    boolean alreadyInTarget = targetBrands.stream()
                            .anyMatch(b -> b.getId().equals(brand.getId()));

                    if (alreadyInTarget) {
                        SwingUtilities.invokeLater(() -> Toast.show(panel, Toast.Type.WARNING,
                                brand.getName() + " markası zaten '" + targetType.getName() + "' türünde kayıtlı."));
                        return;
                    }

                    deviceDictService.unlinkBrandFromType(sourceType.getId(), brand.getId())
                            .thenCompose(v -> deviceDictService.linkBrandToType(targetType.getId(), brand.getId()))
                            .thenAccept(v -> SwingUtilities.invokeLater(() -> {
                                panel.loadBrands(sourceType);
                                panel.loadBrands(targetType);

                                Toast.show(panel, Toast.Type.INFO,
                                        "↔️ " + brand.getName() + " markası '" + sourceType.getName() + "' türünden '" + targetType.getName() + "' türüne taşındı.");
                            }));
                });
            }
            return true;
        } catch (UnsupportedFlavorException | IOException ex) {
            Servicio.getLogger().error("", ex);
        }
        return false;
    }
}

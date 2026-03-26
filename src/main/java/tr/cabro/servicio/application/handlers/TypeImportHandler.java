package tr.cabro.servicio.application.handlers;

import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.panels.setting.SettingsDevicePanel;
import tr.cabro.servicio.settings.DeviceSettings;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

public class TypeImportHandler extends TransferHandler {
    private final SettingsDevicePanel panel;
    private final DeviceSettings settings;

    public TypeImportHandler(SettingsDevicePanel panel) {
        this.panel = panel;
        this.settings = Servicio.getDeviceSettings();
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
            String targetType = panel.type_list.getModel().getElementAt(index);

            String sourceType = panel.type_list.getSelectedValue();

            if (sourceType != null && targetType != null && !sourceType.equals(targetType)) {
                if (settings.removeBrand(sourceType, brandName)) {
                    settings.addBrand(targetType, brandName);

                    // Kaynak ve hedef listeleri sıralı şekilde yenile
                    panel.loadBrands(sourceType);
                    panel.loadBrands(targetType);

                    Toast.show(panel, Toast.Type.INFO,
                            "↔️ " + brandName + " markası '" + sourceType + "' türünden '" + targetType + "' türüne taşındı.");
                }

            }
            return true;
        } catch (UnsupportedFlavorException | IOException ex) {
            Servicio.getLogger().error("", ex);
        }
        return false;
    }
}

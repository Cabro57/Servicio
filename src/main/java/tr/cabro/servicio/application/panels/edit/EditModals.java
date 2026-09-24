package tr.cabro.servicio.application.panels.edit;

import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.model.Product;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.awt.*;

/**
 * Detay sayfalarından (FormPart, FormProduct, FormSupplier) açılan "Düzenle" pencereleri. Liste
 * sayfaları kendi modallarını kuruyordu; detay sayfası aynı işi yaptığında kaydetme, bildirim ve
 * hata akışı tek yerde kalsın diye burada toplandı (bkz. {@link DeviceEditPanel#open}).
 */
public final class EditModals {

    private EditModals() {}

    private static SimpleModalBorder.Option[] updateOptions() {
        return new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Güncelle", SimpleModalBorder.OK_OPTION),
                new SimpleModalBorder.Option("İptal", SimpleModalBorder.CANCEL_OPTION)
        };
    }

    public static void editPart(Component owner, Part part, Runnable onSaved) {
        PartEditPanel panel = new PartEditPanel(part);
        AppModal.showModal(owner, new SimpleModalBorder(panel, "Parça Düzenle", updateOptions(), (controller, action) -> {
            if (action != SimpleModalBorder.OK_OPTION) return;
            Part updated = panel.getData();
            if (updated == null) { controller.consume(); return; }
            ServiceManager.getPartService().save(updated, true).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                Toast.show(owner, Toast.Type.SUCCESS, Messages.get("toast.entity.updated", updated.getName()));
                if (onSaved != null) onSaved.run();
            })).exceptionally(ex -> {
                SwingUtilities.invokeLater(controller::consume);
                return ErrorHandler.handle(owner, "Parça güncellenemedi", ex);
            });
        }), "PartEdit");
    }

    public static void editProduct(Component owner, Product product, Runnable onSaved) {
        ProductEditPanel panel = new ProductEditPanel(product);
        AppModal.showModal(owner, new SimpleModalBorder(panel, "Ürün Düzenle", updateOptions(), (controller, action) -> {
            if (action != SimpleModalBorder.OK_OPTION) return;
            Product updated = panel.getData();
            if (updated == null) { controller.consume(); return; }
            ServiceManager.getProductService().save(updated, true).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                Toast.show(owner, Toast.Type.SUCCESS, Messages.get("toast.entity.updated", updated.getName()));
                if (onSaved != null) onSaved.run();
            })).exceptionally(ex -> {
                SwingUtilities.invokeLater(controller::consume);
                return ErrorHandler.handle(owner, "Ürün güncellenemedi", ex);
            });
        }), "ProductEdit");
    }

    public static void editSupplier(Component owner, Supplier supplier, Runnable onSaved) {
        SupplierEditPanel panel = new SupplierEditPanel(supplier);
        AppModal.showModal(owner, new SimpleModalBorder(panel, "Tedarikçi Düzenle", updateOptions(), (controller, action) -> {
            if (action != SimpleModalBorder.OK_OPTION) return;
            Supplier updated = panel.getData();
            if (updated == null) { controller.consume(); return; }
            updated.setId(supplier.getId());
            updated.setCreatedAt(supplier.getCreatedAt());
            ServiceManager.getSupplierService().save(updated, true).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                Toast.show(owner, Toast.Type.SUCCESS, Messages.get("toast.entity.updated", saved.getName()));
                if (onSaved != null) onSaved.run();
            })).exceptionally(ex -> {
                SwingUtilities.invokeLater(controller::consume);
                return ErrorHandler.handle(owner, "Tedarikçi güncellenemedi", ex);
            });
        }), "SupplierEdit");
    }
}

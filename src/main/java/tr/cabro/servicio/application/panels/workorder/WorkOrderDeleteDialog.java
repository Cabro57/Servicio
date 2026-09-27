package tr.cabro.servicio.application.panels.workorder;

import raven.modal.Toast;
import tr.cabro.servicio.application.component.MessageModal;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Toasts;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.WorkOrderItem;
import tr.cabro.servicio.model.enums.ItemType;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Servis kaydı silme onayı (servis sayfası ve müşteri sayfasındaki liste ortak).
 * <p>
 * Serviste stoktan düşülmüş parça varsa "stoğa geri dönsün" kutusu çıkar, varsayılan işaretli:
 * yanlış açılmış kayıt silinirken parçalar rafa döner. İşaret kaldırılırsa parçalar kullanılmış
 * sayılır ve stok geçmişinde "Servis" çıkışı olarak kalır.
 */
public final class WorkOrderDeleteDialog {

    private WorkOrderDeleteDialog() {}

    /**
     * Onayı açar. Kalemler listeden gelen nesneye güvenilmeden DB'den okunur: liste sayfaları kalemleri
     * her zaman taşımıyor, "stoğa dönsün" kutusu ise gerçekten düşülmüş parça varsa görünmeli.
     */
    public static void confirm(Component parent, WorkOrder workOrder, Runnable onDeleted) {
        ServiceManager.getWorkOrderService().getItems(workOrder.getId())
                .thenAccept(items -> SwingUtilities.invokeLater(() -> show(parent, workOrder, items, onDeleted)))
                .exceptionally(ex -> ErrorHandler.handle(parent, "Servis kalemleri okunamadı", ex));
    }

    private static void show(Component parent, WorkOrder workOrder, List<WorkOrderItem> items, Runnable onDeleted) {
        int usedParts = 0;
        if (workOrder.getServiceStatus() != ServiceStatus.RETURN) {
            for (WorkOrderItem item : items) {
                if (item.getItemType() == ItemType.PART && item.getPartId() != null && item.getQuantity() != null) {
                    usedParts += item.getQuantity();
                }
            }
        }

        MessageModal modal = MessageModal.of(MessageModal.Tone.DANGER, Messages.get("confirm.delete.title"),
                Messages.get("confirm.delete.workorder", workOrder.getId()));
        final boolean hasParts = usedParts > 0;
        JCheckBox restore = new JCheckBox("Kullanılan " + usedParts + " adet parça stoğa geri dönsün", true);
        if (hasParts) modal.extra(restore);

        modal.primary(Messages.get("dialog.button.delete"), () ->
                ServiceManager.getWorkOrderService().delete(workOrder.getId(), !hasParts || restore.isSelected())
                        .thenAccept(v -> SwingUtilities.invokeLater(() -> {
                            Toasts.show(parent, Toast.Type.SUCCESS, Messages.get("toast.record.deleted"));
                            onDeleted.run();
                        }))
                        .exceptionally(ex -> ErrorHandler.handle(parent, "Servis kaydı silinemedi", ex))
        ).show(parent);
    }
}

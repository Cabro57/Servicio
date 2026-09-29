package tr.cabro.servicio.application.panels.workorder;

import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.DateTimeField;
import tr.cabro.servicio.application.component.MessageModal;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.model.WorkOrderStatusHistory;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.WorkOrderService;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * Servis durumuyla ilgili karar kartları: durum değiştirme (geçiş tarihiyle), bir geçişin
 * tarihini düzeltme ve teslimi/iadeyi geri alma. Tarih kuralları (gelecek olamaz, sıra
 * bozulamaz) service katmanında; burada ihlal hata bildirimi olarak döner.
 */
public final class WorkOrderStatusDialogs {

    private WorkOrderStatusDialogs() {}

    /**
     * Yeni durumu seçilen tarihle kaydeder. Teslim ve iade kaydı kilitlediği için uyarı tonunda
     * açılır ve ne kilitleneceğini söyler; diğer geçişler yalnızca tarih sorar.
     *
     * @param since bir önceki geçişin tarihi (takvimde daha eskisi seçilemez); bilinmiyorsa null
     */
    public static void changeStatus(Component parent, Long workOrderId, ServiceStatus newStatus,
                                    LocalDateTime since, Runnable onChanged) {
        DateTimeField field = new DateTimeField();
        field.setEarliest(since);

        boolean closing = newStatus.isClosed();
        String title = closing
                ? (newStatus == ServiceStatus.DELIVERED ? "Cihaz teslim edilsin mi?" : "Servis iade edilsin mi?")
                : "Durum: " + newStatus.getDisplayName();
        String message = closing
                ? "Kayıt kilitlenir: parça, işçilik, not ve arıza tespiti değiştirilemez. Ödeme almaya devam edebilirsiniz."
                        + (newStatus == ServiceStatus.RETURN ? " Servisteki parçalar stoğa geri döner." : "")
                : "Geçiş ne zaman oldu? Şimdi değilse tarihi ve saati düzeltin.";
        String action = switch (newStatus) {
            case DELIVERED -> "Teslim et";
            case RETURN -> "İade et";
            default -> "Durumu değiştir";
        };

        MessageModal.of(closing ? MessageModal.Tone.WARNING : MessageModal.Tone.INPUT, title, message)
                .extra(labeled(closing ? "Teslim tarihi" : "Geçiş tarihi", field))
                .focus(field.focusTarget())
                .primary(action, () -> submit(parent, "Servis durumu güncellenemedi",
                        () -> service().updateStatus(workOrderId, newStatus, field.getValue()), onChanged))
                .show(parent);
    }

    /** Bir geçişin tarihini düzeltir; yeni tarih komşu geçişlerin arasında kalmalıdır. */
    public static void editDate(Component parent, WorkOrderStatusHistory row, Runnable onChanged) {
        DateTimeField field = new DateTimeField();
        field.setValue(row.getChangedAt());

        String label = row.getStatus() == ServiceStatus.ACCEPTED ? "Teslim alma tarihi" : row.getStatus().getDisplayName() + " tarihi";
        MessageModal.of(MessageModal.Tone.INPUT, label + "ni düzelt",
                        "Şu an: " + row.getChangedAt().format(DateFormats.dateTime())
                                + ". Tarih önceki ve sonraki durumların arasında kalmalı.")
                .extra(labeled(label, field))
                .focus(field.focusTarget())
                .primary("Tarihi kaydet", () -> submit(parent, "Durum tarihi güncellenemedi",
                        () -> service().updateStatusDate(row.getId(), field.getValue()), onChanged))
                .show(parent);
    }

    /**
     * Teslimi ya da iadeyi geri alır; kayıt bir önceki durumuna döner ve kilidi açılır.
     *
     * @param onReopened dönülen durumla çağrılır
     */
    public static void reopen(Component parent, Long workOrderId, ServiceStatus closedStatus,
                              Consumer<ServiceStatus> onReopened) {
        boolean returned = closedStatus == ServiceStatus.RETURN;
        MessageModal.of(MessageModal.Tone.WARNING,
                        returned ? "İade geri alınsın mı?" : "Teslim geri alınsın mı?",
                        (returned ? "İade tarihi silinir" : "Teslim tarihi silinir")
                                + ", kayıt bir önceki durumuna döner ve yeniden düzenlenebilir."
                                + (returned ? " Servisteki parçalar yeniden stoktan düşülür." : ""))
                .primary(returned ? "İadeyi geri al" : "Teslimi geri al", () -> service().reopen(workOrderId)
                        .thenAccept(previous -> SwingUtilities.invokeLater(() -> onReopened.accept(previous)))
                        .exceptionally(ex -> ErrorHandler.handle(parent, "Kayıt yeniden açılamadı", ex)))
                .show(parent);
    }

    /**
     * Service çağrısını çalıştırır. Tarih kuralı ihlalleri (gelecek tarih, boş tarih) çağrı anında
     * fırlatılır, sıra ihlalleri future'dan gelir; ikisi de aynı hata bildirimine düşer.
     */
    private static void submit(Component parent, String logContext,
                               java.util.function.Supplier<java.util.concurrent.CompletableFuture<Void>> call, Runnable onDone) {
        try {
            call.get().thenRun(() -> SwingUtilities.invokeLater(onDone))
                    .exceptionally(ex -> ErrorHandler.handle(parent, logContext, ex));
        } catch (RuntimeException ex) {
            ErrorHandler.handle(parent, logContext, ex);
        }
    }

    private static JPanel labeled(String caption, JComponent field) {
        JPanel p = new JPanel(new MigLayout("insets 0, wrap, fillx, gap 0 4", "[grow, fill]", ""));
        p.setOpaque(false);
        p.add(WorkOrderPanelSupport.createCaption(caption));
        p.add(field, "growx, wmin 0");
        return p;
    }

    private static WorkOrderService service() {
        return ServiceManager.getWorkOrderService();
    }
}

package tr.cabro.servicio.application.utils;

import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.documents.DocumentFormat;
import tr.cabro.servicio.documents.DocumentRequest;
import tr.cabro.servicio.documents.PdfDocumentBuilder;
import tr.cabro.servicio.documents.ServiceFormType;
import tr.cabro.servicio.documents.print.PdfPrinter;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.Business;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.settings.AppSettings;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Arayüzden doğrudan yazdırma: PDF arka planda üretilir, {@link PdfPrinter} kuyruğuyla rolün
 * yazıcısına gönderilir, sonuç bildirim olarak gösterilir. Hata kullanıcıya bildirilir ama akışı
 * (satış, servis kaydı) hiçbir zaman geri almaz — belge her zaman sonradan yeniden basılabilir.
 */
public final class PrintActions {

    /** Yazdırılacak PDF'i üretir (arka planda çağrılır). */
    @FunctionalInterface
    public interface PdfSource {
        File produce() throws Exception;
    }

    private PrintActions() {
    }

    public static CompletableFuture<Void> print(Component owner, PdfPrinter.Role role, String jobName, PdfSource source) {
        return CompletableFuture.supplyAsync(() -> {
                    try {
                        return source.produce();
                    } catch (Exception ex) {
                        throw new CompletionException(ex);
                    }
                })
                .thenCompose(pdf -> PdfPrinter.printAsync(pdf, role, jobName))
                .thenAccept(printer -> SwingUtilities.invokeLater(() ->
                        Toasts.show(owner, Toast.Type.SUCCESS, Messages.get("toast.print.sent", printer))))
                .exceptionally(ex -> {
                    Throwable cause = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
                    Servicio.getLogger().error("Yazdırma hatası: {}", jobName, cause);
                    String reason = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
                    SwingUtilities.invokeLater(() ->
                            Toasts.show(owner, Toast.Type.ERROR, Messages.get("toast.print.failed", reason)));
                    return null;
                });
    }

    /**
     * Yeni servis kaydının cihaz kabul fişini, Ayarlar &gt; Yazdırma'da açıksa fiş yazıcısına basar.
     * Kayıt, müşteri ve cihazıyla birlikte veritabanından yeniden okunur; kaydedilen nesnede
     * ilişkiler dolu olmayabilir.
     */
    public static void autoPrintIntakeSlip(Component owner, WorkOrder saved) {
        if (saved == null || saved.getId() == null
                || !AppSettings.get().getPrinting().isAutoPrintIntakeSlip()) {
            return;
        }
        CompletableFuture<Optional<WorkOrder>> orderFuture = ServiceManager.getWorkOrderService().get(saved.getId());
        CompletableFuture<Optional<Business>> shopFuture = ServiceManager.getBusinessService().get();
        CompletableFuture.allOf(orderFuture, shopFuture).thenRun(() -> {
            WorkOrder order = orderFuture.join().orElse(saved);
            Business shop = shopFuture.join().orElse(null);
            ServiceFormType type = ServiceFormType.DEVICE_INTAKE_SLIP;
            print(owner, PdfPrinter.Role.RECEIPT, type.getDisplayName() + " SRV-" + order.getId(),
                    () -> type.generate(order, shop, DocumentRequest.defaults(), DocumentFormat.PDF,
                            PdfDocumentBuilder.tempFile(type.getFileSlug())));
        }).exceptionally(ex -> ErrorHandler.handle(owner, "Kabul fişi için servis kaydı okunamadı", ex));
    }
}

package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.util.SystemFileChooser;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.database.BackupManager;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.service.exception.ValidationException;
import tr.cabro.servicio.settings.AppSettings;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Yedekten geri yükleme penceresi: yedek önce arka planda açılıp doğrulanır, operatör tarihini,
 * sürümünü ve kayıt sayılarını gördükten sonra onaylar. Ayarlar &gt; Yedekleme ve ilk kurulum
 * ekranı aynı akışı kullanır.
 */
public final class BackupRestoreDialog {

    private BackupRestoreDialog() {
    }

    /** Diskteki herhangi bir yedeği seçtirir (ör. USB'deki yedek, yeni bilgisayara kurulum). */
    public static void chooseFile(Component parent, Runnable onRestored) {
        SystemFileChooser chooser = new SystemFileChooser(AppSettings.getBackupDir().getAbsolutePath());
        chooser.setDialogTitle("Geri yüklenecek yedeği seçin");
        chooser.setFileFilter(new SystemFileChooser.FileNameExtensionFilter("Servicio yedeği (*.zip, *.db)", "zip", "db"));
        if (chooser.showOpenDialog(parent) == SystemFileChooser.APPROVE_OPTION) {
            show(parent, chooser.getSelectedFile(), onRestored);
        }
    }

    public static void show(Component parent, File source, Runnable onRestored) {
        SettingsDialog d = new SettingsDialog("Yedek geri yüklensin mi?", 460);
        d.lead("Şu anki veriler bu yedekteki haliyle değiştirilir; yedekten sonra girilen kayıtlar görünmez olur. "
                + "Başlamadan önce mevcut verinin yedeği otomatik alınır, gerekirse ondan geri dönebilirsiniz.");
        JPanel body = d.body();
        JLabel checking = SettingsDialog.impact("icons/clock.svg", "Yedek kontrol ediliyor…");
        body.add(checking, "span 2");

        BackupManager.RestorePlan[] plan = new BackupManager.RestorePlan[1];
        d.primary("Geri yükle", true, () -> {
            if (plan[0] == null) return;
            d.busy();
            // Bilerek EDT'de: geri yükleme sürerken başka ekranlar kapanan havuza sorgu atmasın.
            SwingUtilities.invokeLater(() -> {
                try {
                    BackupManager.apply(plan[0]);
                    d.close();
                    onRestored.run();
                } catch (ValidationException ex) {
                    // Hazırlanan dosya kullanıldı; yeniden denemek için yedek yeniden seçilmeli.
                    d.status(ex.getMessage(), "icons/circle-alert.svg", "Servicio.dangerColor");
                } catch (Exception ex) {
                    d.close();
                    ErrorHandler.handle(parent, "Yedek geri yüklenemedi", ex);
                }
            });
        });
        d.setPrimaryEnabled(false);
        d.show(parent, null);

        CompletableFuture.supplyAsync(() -> {
            try {
                return BackupManager.prepare(source);
            } catch (java.io.IOException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((prepared, ex) -> SwingUtilities.invokeLater(() -> {
            body.remove(checking);
            if (ex != null) {
                Throwable root = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
                String message = root instanceof ValidationException ? root.getMessage()
                        : "Yedek okunamadı: " + root.getMessage();
                d.status(message, "icons/circle-alert.svg", "Servicio.dangerColor");
            } else {
                plan[0] = prepared;
                preview(body, prepared);
                d.setPrimaryEnabled(true);
            }
            body.revalidate();
            body.repaint();
        }));
    }

    private static void preview(JPanel body, BackupManager.RestorePlan p) {
        body.add(SettingsDialog.impact("icons/calendar.svg",
                "Yedek tarihi: " + p.createdAt().format(DateFormats.dateTime())), "span 2");
        String version = p.appVersion() != null ? "Servicio " + p.appVersion() : "Eski biçim yedek";
        if (p.schemaVersion() != null) version += " · şema V" + p.schemaVersion();
        body.add(SettingsDialog.impact("icons/info.svg", version), "span 2");
        body.add(SettingsDialog.impact("icons/users.svg",
                p.customers() + " müşteri · " + p.workOrders() + " servis kaydı"), "span 2");
        body.add(SettingsDialog.impact("icons/lock.svg", p.hasKey()
                ? "Cihaz erişim bilgileri (PIN/şifre) de geri yüklenir."
                : "Yedekte şifre anahtarı yok; cihaz PIN/şifreleri yalnızca yedek bu bilgisayarda alındıysa okunur."), "span 2, wmin 0");
    }
}

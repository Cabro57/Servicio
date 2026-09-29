package tr.cabro.servicio.application.panels.setting;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.utils.Toasts;
import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemFileChooser;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.database.BackupScheduler;
import tr.cabro.servicio.database.BackupManager;
import tr.cabro.servicio.i18n.AppLocale;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.enums.BackupMode;
import tr.cabro.servicio.settings.AppConfig;
import tr.cabro.servicio.settings.AppSettings;
import tr.cabro.servicio.util.DesktopHelper;
import tr.cabro.servicio.util.DialogHelper;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

/**
 * Ayarlar &gt; Sistem &gt; Yedekleme.
 * <p>
 * Üstte yedeklerin sağlığı (son yedek ne zaman, kaç yedek var), altında otomatik yedekleme
 * planı, yedek klasörü ve geri yüklenebilir yedeklerin listesi. Plan ve klasör makineye özel
 * olduğu için {@code config.json} içinde tutulur ve değiştiği anda zamanlayıcıya uygulanır.
 */
public class SettingsDatabasePanel extends JPanel implements SettingsModal.HeaderActions {

    /** Bu kadar günden eski son yedek uyarı rengiyle gösterilir. */
    private static final int STALE_DAYS = 7;

    private final AppConfig.Backup backup = AppSettings.get().getBackup();

    private final JButton backupNowButton = new JButton("Şimdi yedekle", new Ikon("icons/database-backup.svg", 16, "Servicio.onAccentForeground"));

    private JLabel statusIcon;
    private JLabel statusTitle;
    private JLabel statusDetail;
    private JComboBox<BackupMode> modeCombo;
    private JSpinner intervalSpinner;
    private JLabel intervalUnit;
    private JLabel intervalLabel;
    private JLabel nextBackupLabel;
    private JTextField folderField;
    private DefaultListModel<BackupManager.Entry> backupModel;
    private JList<BackupManager.Entry> backupList;
    private JButton restoreButton;
    private JLabel emptyLabel;
    /** Veri klasörüne düşmüş yedekler; yol karşılaştırması disk erişimi gerektirdiği için arka planda hesaplanır. */
    private Set<File> fallbackFiles = Set.of();

    public SettingsDatabasePanel() {
        setLayout(new MigLayout("fill, insets 0", "[grow, fill]", "[top]"));
        setOpaque(false);
        add(createPage());

        backupNowButton.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 5,14,5,14; iconTextGap: 6; font: bold; "
                + "background: $Component.accentColor; foreground: $Servicio.onAccentForeground");
        backupNowButton.addActionListener(e -> backupNow());

        refreshSchedule();
        refreshBackups();
    }

    @Override
    public List<JComponent> headerActions() {
        return List.of(backupNowButton);
    }

    private JPanel createPage() {
        JPanel page = SettingsKit.page();

        // --- Durum ---
        statusIcon = new JLabel();
        statusTitle = new JLabel();
        statusTitle.putClientProperty(FlatClientProperties.STYLE, "font: bold +1");
        statusDetail = SettingsKit.note("");
        JPanel status = new JPanel(new MigLayout("insets 0, gapx 10, gapy 2", "[][grow]", "[][]"));
        status.setOpaque(false);
        status.add(statusIcon, "spany 2, aligny top, gaptop 2");
        status.add(statusTitle, "wrap");
        status.add(statusDetail, "wmin 0");
        SettingsKit.section(page, "Durum", "Veritabanının en son ne zaman yedeklendiği.", status);

        // --- Otomatik yedekleme ---
        modeCombo = new JComboBox<>(BackupMode.values());
        modeCombo.setSelectedItem(backup.getMode());
        intervalSpinner = new JSpinner(new SpinnerNumberModel(Math.max(1, backup.getInterval()), 1, 365, 1));
        intervalLabel = SettingsKit.label("Her");
        intervalUnit = SettingsKit.note("");
        nextBackupLabel = SettingsKit.note("");

        JPanel interval = new JPanel(new MigLayout("insets 0, gap 8", "[70!][]", "[center]"));
        interval.setOpaque(false);
        interval.add(intervalSpinner, "growx");
        interval.add(intervalUnit);

        JPanel schedule = SettingsKit.rows();
        schedule.add(SettingsKit.label("Ne zaman"));
        schedule.add(modeCombo, SettingsKit.FIELD);
        schedule.add(intervalLabel);
        schedule.add(interval, "growx 0");
        schedule.add(nextBackupLabel, "skip");
        SettingsKit.section(page, "Otomatik yedekleme", "Yedek, seçilen zamanda kendiliğinden alınır.", schedule);

        modeCombo.addActionListener(e -> {
            backup.setMode((BackupMode) modeCombo.getSelectedItem());
            AppSettings.save();
            BackupScheduler.restart();
            refreshSchedule();
            SettingsKit.saved(this);
        });
        intervalSpinner.addChangeListener(e -> {
            backup.setInterval((int) intervalSpinner.getValue());
            AppSettings.save();
            BackupScheduler.restart();
            refreshSchedule();
            SettingsKit.saved(this);
        });

        // --- Klasör ---
        folderField = new JTextField();
        folderField.setEditable(false);
        JButton changeFolder = new JButton("Değiştir…");
        changeFolder.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,12,4,12");
        changeFolder.addActionListener(e -> chooseFolder());
        JButton openFolder = new JButton(new Ikon("icons/folder.svg", 16));
        openFolder.setToolTipText("Klasörü aç");
        openFolder.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,8,4,8");
        openFolder.addActionListener(e -> {
            File dir = AppSettings.getBackupDir();
            dir.mkdirs();
            DesktopHelper.openFile(dir);
        });

        JPanel folder = new JPanel(new MigLayout("insets 0, gap 6, fillx", "[grow, fill][][]", "[center]"));
        folder.setOpaque(false);
        folder.add(folderField, "wmin 0");
        folder.add(changeFolder);
        folder.add(openFolder);
        JPanel folderStack = SettingsKit.stack();
        folderStack.add(folder, "wmin 0");
        folderStack.add(SettingsKit.wrappingNote("Yedekleri ayrı bir diskte ya da bulutla eşitlenen bir klasörde tutmak, "
                + "bilgisayar arızalandığında verinizi kurtarır."), "wmin 0, wmax 420");
        SettingsKit.section(page, "Klasör", "Yedek dosyalarının kaydedildiği yer.", folderStack);

        // --- Yedekler ---
        backupModel = new DefaultListModel<>();
        backupList = new JList<>(backupModel);
        backupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        backupList.setCellRenderer(new BackupRenderer());
        backupList.putClientProperty(FlatClientProperties.STYLE_CLASS, "dashboardBackground");
        backupList.addListSelectionListener(e -> restoreButton.setEnabled(backupList.getSelectedValue() != null));

        JScrollPane listScroll = new JScrollPane(backupList);
        listScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        emptyLabel = SettingsKit.note("Henüz yedek yok. Başlıktaki \"Şimdi yedekle\" ile ilk yedeği alın.");

        restoreButton = new JButton("Seçili yedeği geri yükle", new Ikon("icons/undo-2.svg", 16));
        restoreButton.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 5,12,5,12; iconTextGap: 6");
        restoreButton.setEnabled(false);
        restoreButton.addActionListener(e -> restoreSelectedBackup());
        JButton fromFile = new JButton("Dosyadan geri yükle…", new Ikon("icons/folder-search.svg", 16));
        fromFile.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 5,12,5,12; iconTextGap: 6");
        fromFile.setToolTipText("Başka bir klasördeki ya da USB bellekteki yedeği seçin");
        fromFile.addActionListener(e -> BackupRestoreDialog.chooseFile(this, this::afterRestore));

        JPanel backups = SettingsKit.stack();
        backups.add(emptyLabel);
        backups.add(listScroll, "h 90:220:220, wmin 0");
        JPanel restoreRow = new JPanel(new MigLayout("insets 0, gap 8, hidemode 3", "[][]", "[center]"));
        restoreRow.setOpaque(false);
        restoreRow.add(restoreButton);
        restoreRow.add(fromFile);
        backups.add(restoreRow, "wmin 0");
        backups.add(SettingsKit.wrappingNote("Geri yüklemeden önce mevcut verinin yedeği otomatik alınır. Otomatik yedeklerden "
                + "en yeni 20 tanesi ve son 30 günün her gününden biri saklanır; elle alınan yedekler silinmez."), "wmin 0, wmax 420");
        SettingsKit.section(page, "Yedekler", "En yeni yedek en üstte. Geri yüklemeden sonra yeniden giriş yapılır.", backups);

        return page;
    }

    // ------------------------------------------------------------------ durum

    private void refreshSchedule() {
        BackupMode mode = (BackupMode) modeCombo.getSelectedItem();
        String unit = unitOf(mode);
        boolean needsInterval = unit != null;
        intervalLabel.setVisible(needsInterval);
        intervalSpinner.getParent().setVisible(needsInterval);
        intervalUnit.setText(unit != null ? unit : "");

        LocalDateTime next = BackupScheduler.getNextBackupTime();
        if (mode == BackupMode.NONE) {
            nextBackupLabel.setText("Otomatik yedekleme kapalı; yalnızca elle yedek alınır.");
        } else if (next != null) {
            nextBackupLabel.setText("Sonraki yedek: " + next.format(DateFormats.dateTime()));
        } else {
            nextBackupLabel.setText("Yedek, uygulama açılırken ya da kapanırken alınır.");
        }
        revalidate();
    }

    /** Aralık birimi, "Her [15] dakikada bir" okunacak biçimde; aralık istemeyen modlarda {@code null}. */
    private static String unitOf(BackupMode mode) {
        if (mode == null) return null;
        return switch (mode) {
            case EVERY_N_MINUTES -> "dakikada bir";
            case EVERY_N_HOURS -> "saatte bir";
            case EVERY_N_DAYS -> "günde bir";
            case EVERY_N_WEEKS -> "haftada bir";
            case EVERY_N_MONTHS -> "ayda bir";
            default -> null;
        };
    }

    private void refreshBackups() {
        File dir = AppSettings.getBackupDir();
        folderField.setText(dir.getAbsolutePath());
        folderField.setCaretPosition(0);

        // Klasör USB ya da ağ sürücüsünde olabilir; listeleme EDT'yi bekletmesin.
        CompletableFuture.supplyAsync(() -> {
            List<BackupManager.Entry> entries = BackupManager.listAll();
            Set<File> fallback = entries.stream().filter(BackupManager::isInFallback)
                    .map(BackupManager.Entry::file).collect(Collectors.toSet());
            return new BackupState(entries, fallback, BackupManager.currentProblem());
        }).thenAccept(state -> SwingUtilities.invokeLater(() -> {
            // Bu arada klasör değiştiyse eski sonuç gösterilmez.
            if (!dir.equals(AppSettings.getBackupDir())) return;
            fallbackFiles = state.fallback();
            showBackups(state.entries(), state.problem());
        }));
    }

    private record BackupState(List<BackupManager.Entry> entries, Set<File> fallback, String problem) {
    }

    private void showBackups(List<BackupManager.Entry> sorted, String problem) {
        backupModel.clear();
        sorted.forEach(backupModel::addElement);
        boolean empty = sorted.isEmpty();
        emptyLabel.setVisible(empty);
        backupList.getParent().getParent().setVisible(!empty);
        restoreButton.setVisible(!empty);
        restoreButton.setEnabled(false);

        updateStatus(empty ? null : sorted.get(0), sorted.size(), problem);
        revalidate();
        repaint();
    }

    private void updateStatus(BackupManager.Entry latest, int count, String problem) {
        if (latest == null) {
            statusIcon.setIcon(new Ikon("icons/circle-x.svg", 20, "Servicio.dangerColor"));
            statusTitle.setText("Hiç yedek yok");
            statusDetail.setText(problem != null ? problem
                    : "Bilgisayar arızalanırsa veriler kurtarılamaz. Şimdi bir yedek alın.");
            return;
        }
        Duration age = Duration.between(latest.createdAt(), LocalDateTime.now());
        boolean warn = age.toDays() >= STALE_DAYS || problem != null;
        statusIcon.setIcon(new Ikon(warn ? "icons/triangle-alert.svg" : "icons/circle-check.svg", 20,
                warn ? "Servicio.warningColor" : "Servicio.successColor"));
        statusTitle.setText("Son yedek " + relative(age));
        String detail = latest.createdAt().format(DateFormats.dateTime()) + " · " + fileSize(latest.file().length())
                + " · toplam " + count + " yedek";
        statusDetail.setText(problem != null ? problem : detail);
    }

    // ------------------------------------------------------------------ eylemler

    private void backupNow() {
        backupNowButton.setEnabled(false);
        backupNowButton.setText("Yedekleniyor…");
        new SwingWorker<File, Void>() {
            @Override
            protected File doInBackground() throws Exception {
                return BackupManager.create(BackupManager.Kind.MANUAL);
            }

            @Override
            protected void done() {
                backupNowButton.setEnabled(true);
                backupNowButton.setText("Şimdi yedekle");
                File created = null;
                try {
                    created = get();
                } catch (Exception ex) {
                    Servicio.getLogger().error("Elle yedek alınamadı", ex);
                }
                refreshBackups();
                refreshSchedule();
                if (created != null && BackupManager.isInFallback(created)) {
                    Toasts.show(SettingsDatabasePanel.this, Toast.Type.WARNING,
                            "Yedek klasörüne ulaşılamadı; yedek veri klasörüne alındı: " + created.getName());
                } else if (created != null) {
                    Toasts.show(SettingsDatabasePanel.this, Toast.Type.SUCCESS,
                            Messages.get("toast.backup.done", created.getName()));
                } else {
                    Toasts.show(SettingsDatabasePanel.this, Toast.Type.ERROR, Messages.get("toast.backup.failed"));
                }
            }
        }.execute();
    }

    private void restoreSelectedBackup() {
        BackupManager.Entry selected = backupList.getSelectedValue();
        if (selected == null) {
            DialogHelper.error(this, "backup.select.required");
            return;
        }
        BackupRestoreDialog.show(this, selected.file(), this::afterRestore);
    }

    private void afterRestore() {
        // Ayarlar penceresi kapanır, eski verili formlar temizlenir, giriş ekranına dönülür.
        FormManager.resetAfterRestore();
    }

    private void chooseFolder() {
        SystemFileChooser chooser = new SystemFileChooser(AppSettings.getBackupDir().getAbsolutePath());
        chooser.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == SystemFileChooser.APPROVE_OPTION) {
            AppSettings.setBackupDir(chooser.getSelectedFile());
            AppSettings.save();
            refreshBackups();
            SettingsKit.saved(this);
        }
    }

    // ------------------------------------------------------------------ biçimlendirme

    private static String relative(Duration age) {
        long minutes = age.toMinutes();
        if (minutes < 1) return "az önce";
        if (minutes < 60) return minutes + " dakika önce";
        long hours = age.toHours();
        if (hours < 24) return hours + " saat önce";
        long days = age.toDays();
        if (days == 1) return "dün";
        return days + " gün önce";
    }

    private static String fileSize(long bytes) {
        if (bytes < 1024 * 1024) return String.format(AppLocale.formatLocale(), "%,d KB", Math.max(1, bytes / 1024));
        return String.format(AppLocale.formatLocale(), "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** Yedek satırı: solda tarih ve tür, altında yaş ve dosya adı; sağda boyut. */
    private final class BackupRenderer extends JPanel implements ListCellRenderer<BackupManager.Entry> {
        private final JLabel date = new JLabel();
        private final JLabel age = new JLabel();
        private final JLabel size = new JLabel();

        BackupRenderer() {
            super(new MigLayout("insets 6 12 6 12, gapy 0", "[grow][]", "[][]"));
            date.putClientProperty(FlatClientProperties.STYLE, "font: bold");
            age.putClientProperty(FlatClientProperties.STYLE, "font: -1");
            size.putClientProperty(FlatClientProperties.STYLE, "font: -1");
            add(date);
            add(size, "spany 2, aligny center, wrap");
            add(age);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends BackupManager.Entry> list, BackupManager.Entry entry,
                                                      int index, boolean selected, boolean focused) {
            date.setText(entry.createdAt().format(DateFormats.dateTime()) + " · " + entry.kind().getLabel()
                    + (entry.appVersion() != null ? " · v" + entry.appVersion() : ""));
            age.setText(relative(Duration.between(entry.createdAt(), LocalDateTime.now())) + " · " + entry.file().getName()
                    + (fallbackFiles.contains(entry.file()) ? " · veri klasöründe" : ""));
            size.setText(fileSize(entry.file().length()));

            Color fg = selected ? list.getSelectionForeground() : list.getForeground();
            Color muted = selected ? list.getSelectionForeground() : UIManager.getColor("Label.disabledForeground");
            setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            date.setForeground(fg);
            age.setForeground(muted);
            size.setForeground(muted);
            return this;
        }
    }
}

package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.SystemFileChooser;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.database.BackupScheduler;
import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.model.enums.BackupMode;
import tr.cabro.servicio.settings.AppConfig;
import tr.cabro.servicio.settings.AppSettings;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

public class SettingsDatabasePanel extends JPanel {

    private final AppConfig.Backup backup = AppSettings.get().getBackup();

    public SettingsDatabasePanel() {
        init();
    }

    private void init() {
        initComponent();

        // === Klasör seçici ===
        JButton chooser_folder_button = new JButton(new Ikon("icons/folder-search.svg", 0.7f));
        chooser_folder_button.addActionListener(e -> onFolderChooser());

        folder_path_field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, chooser_folder_button);
        folder_path_field.setText(AppSettings.getBackupDir().getAbsolutePath());

        // === Periyot seçimi ===
        periods_combo.setModel(new DefaultComboBoxModel<>(BackupMode.values()));
        periods_combo.setSelectedItem(backup.getMode());

        interval_spinner.setModel(new SpinnerNumberModel(backup.getInterval(), 1, 365, 1));
        interval_spinner.setEnabled(needsInterval(backup.getMode()));

        periods_combo.addActionListener(e -> {
            BackupMode selected = (BackupMode) periods_combo.getSelectedItem();
            backup.setMode(selected);
            interval_spinner.setEnabled(needsInterval(selected));
            AppSettings.save();
            BackupScheduler.restart();
            updateNextBackupLabel();
        });

        interval_spinner.addChangeListener(e -> {
            int val = (int) interval_spinner.getValue();
            backup.setInterval(val);
            AppSettings.save();
            BackupScheduler.restart();
            updateNextBackupLabel();
        });

        // === Şu anki planlanan yedekleme ===
        updateNextBackupLabel();

        // === Anlık yedek ===
        backup_now_button.addActionListener(e -> {
            DatabaseManager.backup();
            refreshBackupList();
            updateNextBackupLabel();
        });

        rollback_button.addActionListener(e -> restoreSelectedBackup());

        refreshBackupList();
    }

    /** Etiketi günceller */
    private void updateNextBackupLabel() {
        LocalDateTime next = BackupScheduler.getNextBackupTime();
        if (next != null) {
            String formatted = next.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            next_backup_label.setText("Bir sonraki otomatik yedekleme: " + formatted);
        } else {
            next_backup_label.setText("Planlanmış yedekleme yok");
        }
    }

    private boolean needsInterval(BackupMode mode) {
        return mode == BackupMode.EVERY_N_MINUTES ||
                mode == BackupMode.EVERY_N_HOURS ||
                mode == BackupMode.EVERY_N_DAYS ||
                mode == BackupMode.EVERY_N_WEEKS ||
                mode == BackupMode.EVERY_N_MONTHS;
    }

    private void refreshBackupList() {
        File dir = AppSettings.getBackupDir();
        if (!dir.exists()) dir.mkdirs();
        String[] files = dir.list((d, name) -> name.endsWith(".db") || name.endsWith(".sql"));
        if (files != null) {
            Arrays.sort(files);
            backups_list.setListData(files);
        } else {
            backups_list.setListData(new String[0]);
        }
    }

    private void restoreSelectedBackup() {
        String selected = backups_list.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Lütfen bir yedek seçin.", "Hata", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Seçilen yedeği geri yüklemek istediğinize emin misiniz?\nBu işlem geri alınamaz.",
                "Onay", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        File backupFile = new File(AppSettings.getBackupDir(), selected);
        DatabaseManager.restore(backupFile);

        // Tüm formları sıfırla — eski cached data'yı temizle
        SwingUtilities.invokeLater(() -> {
            FormManager.lock(); // Kilit ekranına at, stack temizlensin
            JOptionPane.showMessageDialog(this,
                    "Yedek geri yüklendi. Lütfen tekrar giriş yapın.",
                    "Bilgi", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private void onFolderChooser() {
        SystemFileChooser chooser = new SystemFileChooser(AppSettings.getBackupDir().getAbsolutePath());
        chooser.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == SystemFileChooser.APPROVE_OPTION) {
            AppSettings.setBackupDir(chooser.getSelectedFile());
            folder_path_field.setText(AppSettings.getBackupDir().getAbsolutePath());
            refreshBackupList();
            AppSettings.save();
        }
    }

    private void initComponent() {
        setLayout(new MigLayout("insets 10, fillx, wrap 2", "[pref!][grow]"));

        // --- Klasör seçici ---
        JLabel folder_label = new JLabel("Yedekleme Klasörü:");
        folder_path_field = new JTextField(AppSettings.getBackupDir().getAbsolutePath());
        JButton chooser_folder_button = new JButton(new FlatSVGIcon("icons/folder.svg"));
        folder_path_field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, chooser_folder_button);

        add(folder_label);
        add(folder_path_field, "growx, wrap");

        // --- Periyot seçimi ---
        JLabel period_label = new JLabel("Yedekleme Periyodu:");
        periods_combo = new JComboBox<>(BackupMode.values());

        backup_now_button = new JButton("Şimdi Yedekle");

        JLabel interval_label = new JLabel("Aralık:");
        interval_spinner = new JSpinner(new SpinnerNumberModel(1, 1, 365, 1));

        add(period_label);
        add(periods_combo, "growx, split 2");
        add(backup_now_button, "wrap");
        add(interval_label);
        add(interval_spinner, "growx, wrap");

        // --- Sonraki yedek bilgisi ---
        next_backup_label = new JLabel("Planlanmış yedekleme yok");
        add(next_backup_label, "span 2, wrap");

        add(new JSeparator(JSeparator.HORIZONTAL), "span 2, growx, pushx, wrap");

        // --- Yedekleme listesi ---
        JLabel backups_label = new JLabel("Yedekler:");
        backups_label.setFont(backups_label.getFont().deriveFont(Font.BOLD));
        backups_list = new JList<>();
        JScrollPane list_scroll = new JScrollPane(backups_list);

        add(backups_label, "span 2, wrap");
        add(list_scroll, "span 2, push, grow, wrap");

        // --- Butonlar ---
        rollback_button = new JButton("Seçili Yedeği Geri Yükle");

        add(rollback_button, "span 2, growx, wrap");
    }

    private JTextField folder_path_field;
    private JComboBox<BackupMode> periods_combo;
    private JList<String> backups_list;
    private JLabel folder_path_label;
    private JLabel period_label;
    private JLabel backups_label;
    private JLabel next_backup_label;
    private JButton backup_now_button;
    private JButton rollback_button;
    private JSpinner interval_spinner;
}

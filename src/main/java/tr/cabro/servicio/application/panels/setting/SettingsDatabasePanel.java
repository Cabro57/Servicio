package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.BackupScheduler;
import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.model.BackupMode;
import tr.cabro.servicio.settings.Settings;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

public class SettingsDatabasePanel extends JPanel {

    private final Settings settings;

    public SettingsDatabasePanel() {
        this.settings = Servicio.getSettings();
        init();
    }

    private void init() {
        initComponent();

        // === Klasör seçici ===
        JButton chooser_folder_button = new JButton(new FlatSVGIcon("icons/folder.svg"));
        chooser_folder_button.addActionListener(e -> onFolderChooser());

        folder_path_field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, chooser_folder_button);
        folder_path_field.setText(settings.getBackup().getPath());

        // === Periyot seçimi ===
        periods_combo.setModel(new DefaultComboBoxModel<>(BackupMode.values()));
        periods_combo.setSelectedItem(settings.getBackup().getMode());

        interval_spinner.setModel(new SpinnerNumberModel(settings.getBackup().getInterval(), 1, 365, 1));
        interval_spinner.setEnabled(needsInterval(settings.getBackup().getMode()));

        periods_combo.addActionListener(e -> {
            BackupMode selected = (BackupMode) periods_combo.getSelectedItem();
            settings.getBackup().setMode(selected);
            interval_spinner.setEnabled(needsInterval(selected));
            settings.save();
            BackupScheduler.restart();
            updateNextBackupLabel();
        });

        interval_spinner.addChangeListener(e -> {
            int val = (int) interval_spinner.getValue();
            settings.getBackup().setInterval(val);
            settings.save();
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
        File dir = new File(settings.getBackup().getPath());
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

        File backupFile = new File(settings.getBackup().getPath(), selected);
        DatabaseManager.restore(backupFile);
        JOptionPane.showMessageDialog(this, "Yedek geri yüklendi.", "Bilgi", JOptionPane.INFORMATION_MESSAGE);
    }

    private void onFolderChooser() {
        JFileChooser chooser = new JFileChooser(settings.getBackup().getPath());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            folder_path_field.setText(path);
            settings.getBackup().setPath(path);
            refreshBackupList();
            settings.save();
        }
    }

    private void initComponent() {
        setLayout(new MigLayout("insets 10, fillx, wrap 2", "[pref!][grow]"));

        // --- Klasör seçici ---
        JLabel folder_label = new JLabel("Yedekleme Klasörü:");
        folder_path_field = new JTextField(settings.getBackup().getPath());
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

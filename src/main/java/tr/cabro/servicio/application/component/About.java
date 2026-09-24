package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.updater.UpdateChecker;
import tr.cabro.servicio.util.DesktopHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.Locale;

/**
 * Hakkında penceresi: uygulamanın kimliği ve sürüm/güncelleme durumu üstte; altında destek
 * isterken sorulan bilgiler (veri klasörü, veritabanı boyutu, Java, sistem) tek tıkla kopyalanabilir.
 */
public class About extends JPanel {

    private static final String REPO_URL = "https://github.com/Cabro57/Servicio";

    private final JLabel updateState = new JLabel();

    public About() {
        init();
    }

    private void init() {
        setLayout(new MigLayout("fillx, wrap, insets 4 24 18 24, width 480, gap 0", "[grow, fill]", ""));

        // --- Kimlik ---
        JLabel logo = new JLabel();
        java.net.URL url = getClass().getResource("/logo.png");
        if (url != null) {
            Image img = new ImageIcon(url).getImage().getScaledInstance(56, 56, Image.SCALE_SMOOTH);
            logo.setIcon(new ImageIcon(img));
        }
        JLabel name = new JLabel("Servicio");
        name.putClientProperty(FlatClientProperties.STYLE, "font: bold +8");
        JLabel version = new JLabel("Sürüm " + Servicio.getInstance().getAppVersion());
        version.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");

        JPanel identity = new JPanel(new MigLayout("insets 0, gap 14 2", "[][grow]", "[bottom][top]"));
        identity.setOpaque(false);
        identity.add(logo, "spany 2, aligny center");
        identity.add(name, "wrap");
        identity.add(version);
        add(identity);

        JLabel description = new JLabel("<html>Teknik servis ve tamir atölyeleri için servis, müşteri, stok, satış ve cari "
                + "hesap takibi. Verileriniz bu bilgisayarda saklanır.</html>");
        description.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        add(description, "gaptop 14, wmin 0");

        // --- Güncelleme durumu ---
        JButton check = new JButton("Güncellemeleri denetle", new Ikon("icons/refresh-cw.svg", 14, "Label.foreground"));
        check.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 5,10,5,10; iconTextGap: 6");
        check.addActionListener(e -> {
            UpdateChecker checker = Servicio.getInstance().getUpdateChecker();
            if (checker != null) checker.checkNow();
            Toast.show(this, Toast.Type.INFO, Messages.get("toast.update.checking"));
        });
        JPanel updateRow = new JPanel(new MigLayout("insets 10 12 10 12, fillx", "[grow][]", "[center]"));
        updateRow.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        updateRow.add(updateState, "wmin 0");
        updateRow.add(check);
        add(updateRow, "gaptop 16");
        refreshUpdateState();

        // --- Destek bilgileri ---
        JLabel supportTitle = new JLabel("Destek bilgileri");
        supportTitle.putClientProperty(FlatClientProperties.STYLE, "font: $h3.font");
        add(supportTitle, "gaptop 18");

        File dataFolder = Servicio.getInstance().getDataFolder();
        JPanel facts = new JPanel(new MigLayout("insets 8 0 0 0, fillx, gap 12 6", "[shrink 0][grow, fill][]", ""));
        facts.setOpaque(false);
        JButton openFolder = new JButton("Aç");
        openFolder.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 2,8,2,8");
        openFolder.setToolTipText("Veri klasörünü dosya yöneticisinde aç");
        openFolder.addActionListener(e -> DesktopHelper.openFile(dataFolder));
        addFact(facts, "Veri klasörü", dataFolder.getAbsolutePath(), openFolder);
        addFact(facts, "Veritabanı", databaseSize(dataFolder), null);
        addFact(facts, "Java", System.getProperty("java.vendor") + " " + System.getProperty("java.version"), null);
        addFact(facts, "Sistem", System.getProperty("os.name") + " " + System.getProperty("os.arch")
                + " (" + System.getProperty("os.version") + ")", null);
        add(facts);

        // --- Alt satır ---
        JButton copy = new JButton("Bilgileri kopyala", new Ikon("icons/clipboard-list.svg", 14, "Label.foreground"));
        copy.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 5,10,5,10; iconTextGap: 6");
        copy.setToolTipText("Destek isterken yapıştırmak için sürüm ve sistem bilgisini panoya kopyalar");
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(supportText(dataFolder)), null);
            Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.about.copied"));
        });
        JButton repo = new JButton("Proje sayfası");
        repo.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        repo.putClientProperty(FlatClientProperties.STYLE, "foreground: $Component.accentColor; margin: 2,6,2,6");
        repo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        repo.addActionListener(e -> DesktopHelper.browseUrl(REPO_URL));
        JPanel footer = new JPanel(new MigLayout("insets 0, fillx", "[]push[]", "[center]"));
        footer.setOpaque(false);
        footer.add(copy);
        footer.add(repo);
        add(footer, "gaptop 16");
    }

    private static void addFact(JPanel facts, String caption, String value, JComponent trailing) {
        JLabel cap = new JLabel(caption);
        cap.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        JLabel val = new JLabel(value);
        val.setToolTipText(value);
        facts.add(cap);
        facts.add(val, "wmin 0" + (trailing == null ? ", span 2, wrap" : ""));
        if (trailing != null) facts.add(trailing, "wrap");
    }

    private void refreshUpdateState() {
        UpdateChecker checker = Servicio.getInstance().getUpdateChecker();
        if (checker != null && checker.hasPendingUpdate() && checker.getPendingUpdate() != null) {
            updateState.setText("Yeni sürüm hazır: v" + checker.getPendingUpdate().getVersion());
            updateState.setIcon(new Ikon("icons/circle-alert.svg", 16, "Servicio.actionColor"));
            updateState.putClientProperty(FlatClientProperties.STYLE, "font: bold; foreground: $Servicio.actionColor");
        } else {
            updateState.setText("Güncel sürümü kullanıyorsunuz");
            updateState.setIcon(new Ikon("icons/check-check.svg", 16, "Servicio.successColor"));
            updateState.putClientProperty(FlatClientProperties.STYLE, "font: bold; foreground: $Servicio.successColor");
        }
        updateState.setIconTextGap(8);
    }

    private static String databaseSize(File dataFolder) {
        File db = new File(new File(dataFolder, "database"), "database.db");
        if (!db.exists()) return "Bulunamadı";
        double mb = db.length() / (1024.0 * 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", mb).replace('.', ',');
    }

    private static String supportText(File dataFolder) {
        return "Servicio " + Servicio.getInstance().getAppVersion() + "\n"
                + "Java: " + System.getProperty("java.vendor") + " " + System.getProperty("java.version") + "\n"
                + "Sistem: " + System.getProperty("os.name") + " " + System.getProperty("os.arch") + " (" + System.getProperty("os.version") + ")\n"
                + "Veri klasörü: " + dataFolder.getAbsolutePath() + "\n"
                + "Veritabanı: " + databaseSize(dataFolder);
    }
}

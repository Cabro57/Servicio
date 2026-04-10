package tr.cabro.servicio.application.forms.example;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.StatCard;

import javax.swing.*;
import java.awt.*;

public class ServiceRecordsPanel extends JPanel {

    private JTable table;
    private JTextField searchField;
    private JComboBox<String> filterCombo;

    public ServiceRecordsPanel() {
        // Ana Layout: Yukarıdan aşağıya 3 satır: Header, Stats, TableArea
        setLayout(new MigLayout("debug, fill, insets 20, gapy 20", "[grow]", "[pref][pref][grow, fill]"));

        buildHeader();
        buildStats();
        buildTableArea();
    }

    private void buildHeader() {
        JPanel headerPanel = new JPanel(new MigLayout("insets 0, fillx", "[grow][]", "[][]"));
        headerPanel.setOpaque(false);

        JLabel title = new JLabel("Servis Kayıtları");
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +14");

        JLabel subtitle = new JLabel("Müşteri cihazlarının teknik servis durumlarını yönetin.");
        subtitle.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");

        JButton btnNew = new JButton("Yeni Kayıt Oluştur");
        // Butonu FlatLaf'ın birincil (Primary) rengine boya ve ikon ekle
        btnNew.putClientProperty(FlatClientProperties.STYLE, "background: $Component.accentColor; foreground: #ffffff; arc: 10; margin: 5,15,5,15");
        // btnNew.setIcon(IconManager.getIcon("icons/plus.svg", 16)); // İkon yolunu kendine göre ayarla

        headerPanel.add(title, "cell 0 0");
        headerPanel.add(btnNew, "cell 1 0 1 2, aligny center"); // Sağda ortala
        headerPanel.add(subtitle, "cell 0 1");

        add(headerPanel, "wrap, growx");
    }

    private void buildStats() {
        // İstatistik kartlarını yan yana dizen panel
        JPanel statsPanel = new JPanel(new MigLayout("insets 0, gapx 15, fillx", "[grow][grow][grow][grow]", "[fill]"));
        statsPanel.setOpaque(false);

        // Renkler resimdeki temaya uygun (Kendi Hex kodlarını buraya girebilirsin)
        Color successColor = Color.decode("#4CAF50");
        Color primaryColor = UIManager.getColor("Component.accentColor");

        // Kartları ekle (Veriler şimdilik statik, Data katmanına bağlayacağız)
        statsPanel.add(new StatCard("Toplam Kayıt", "5", "icons/user.svg", null));
        statsPanel.add(new StatCard("Aktif İşlemler", "2", "icons/user.svg", primaryColor));
        statsPanel.add(new StatCard("Tamamlanan", "2", "icons/user.svg", successColor));
        statsPanel.add(new StatCard("Toplam Ciro", "₺4.650,00", "icons/user.svg", successColor));

        add(statsPanel, "wrap, growx");
    }

    private void buildTableArea() {
        // Tabloyu ve üstündeki arama çubuğunu saran ana arka plan
        JPanel tableContainer = new JPanel(new MigLayout("fill, insets 15", "[grow][]", "[pref][grow]"));
        tableContainer.putClientProperty(FlatClientProperties.STYLE, "arc: 16; background: lighten($Panel.background, 3%);");

        JLabel tableTitle = new JLabel("Tüm Kayıtlar");
        tableTitle.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");

        // Arama ve Filtre
        searchField = new JTextField(20);
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Müşteri, cihaz veya ID ara...");
        searchField.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,10,4,10");
        // searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, IconManager.getIcon("icons/search.svg", 16));

        filterCombo = new JComboBox<>(new String[]{"Tümü", "Aktif", "Tamamlandı", "İptal"});
        filterCombo.putClientProperty(FlatClientProperties.STYLE, "arc: 10");

        JPanel toolbar = new JPanel(new MigLayout("insets 0, gapx 10", "[][grow][][]"));
        toolbar.setOpaque(false);
        toolbar.add(tableTitle);
        toolbar.add(searchField, "cell 2 0");
        toolbar.add(filterCombo, "cell 3 0");

        // Tablo (Şimdilik boş tanımlıyoruz, Virtual Table modelini bağlayacağız)
        table = new JTable();
        table.setRowHeight(50); // İki satırlık veriler için satır yüksekliğini artırdık
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder()); // Çirkin kenarlığı sil

        tableContainer.add(toolbar, "wrap, growx, pushx");
        tableContainer.add(scrollPane, "grow, push");

        add(tableContainer, "grow");
    }
}
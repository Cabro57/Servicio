package tr.cabro.servicio.application.panels.setting;

import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.menu.MyDrawerBuilder;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;

/**
 * Ayarlar &gt; Satış &gt; Menü Görünürlüğü.
 * <p>
 * {@code parts} (servis parçası) ve {@code products} (satış/POS ürünü) V21'den itibaren
 * bağımsız tablolar/kataloglar — burada sadece iki ekranın menüde görünüp görünmeyeceği
 * belirlenir.
 */
public class SettingsPosPanel extends JPanel {

    private JCheckBox menuShowParts;
    private JCheckBox menuShowProducts;

    public SettingsPosPanel() {
        init();
    }

    private void init() {
        initComponent();
        loadSettings();

        menuShowParts.addActionListener(e -> {
            ServiceManager.getAppSettingService().setMenuShowParts(menuShowParts.isSelected());
            MyDrawerBuilder.getInstance().rebuildMenu();
        });
        menuShowProducts.addActionListener(e -> {
            ServiceManager.getAppSettingService().setMenuShowProducts(menuShowProducts.isSelected());
            MyDrawerBuilder.getInstance().rebuildMenu();
        });
    }

    private void loadSettings() {
        menuShowParts.setSelected(ServiceManager.getAppSettingService().isMenuShowParts());
        menuShowProducts.setSelected(ServiceManager.getAppSettingService().isMenuShowProducts());
    }

    private void initComponent() {
        setLayout(new MigLayout("fillx, insets 10, gapy 15", "[grow]", "[][grow]"));

        JPanel menuPanel = new JPanel(new MigLayout("fill, insets 10, wrap 1", "[grow]", "[]"));
        menuPanel.setBorder(BorderFactory.createTitledBorder("Menü Görünürlüğü"));

        menuShowParts = new JCheckBox("\"Parçalar\" menüde görünsün");
        menuShowParts.setToolTipText("Kapatırsanız servis parçası ekranı menüden kaldırılır (sadece perakende satış yapılıyorsa).");
        menuPanel.add(menuShowParts);

        menuShowProducts = new JCheckBox("\"Ürünler\" menüde görünsün");
        menuShowProducts.setToolTipText("Kapatırsanız satış ürünü ekranı menüden kaldırılır (sadece servis yapılıyorsa).");
        menuPanel.add(menuShowProducts);

        add(menuPanel, "growx, wrap");

        add(new JLabel(), "pushy, growy, wrap");
    }
}

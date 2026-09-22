package tr.cabro.servicio.application.panels.secondhand;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.datetime.DatePicker;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.component.CustomerSelectBox;
import tr.cabro.servicio.application.component.CustomerSummaryPanel;
import tr.cabro.servicio.application.component.FormKit;
import tr.cabro.servicio.application.component.WrapLayout;
import tr.cabro.servicio.application.panels.DeviceFormPanel;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * İkinci el alım kaydı formu. Servis kaydı formuyla aynı iki sütunlu düzen: solda kimden
 * (satıcı müşteri) ve ne alınıyor (cihaz), sağda alım bilgisi ve ekspertiz.
 * <ul>
 *   <li>Satıcı seçilince bakiyesi ve sorunlu müşteri uyarısı görünür; daha önce servise getirdiği
 *       cihazlar cihaz bölümünde çip olarak çıkar (servisteki cihazını satıyorsa tek tık).</li>
 *   <li>Cihaz alanları ve IMEI araması {@link DeviceFormPanel}'den gelir.</li>
 *   <li>Ekspertiz çipleri, alımda kontrol edilen maddeleri nota ekler.</li>
 *   <li>Fiyat yalnızca TL girilir: alım kaydı döviz tutmuyor, eskiden USD seçilse bile tutar
 *       kur uygulanmadan TL olarak yazılıyordu.</li>
 * </ul>
 * Doğrulama hataları alanların altında gösterilir ({@link #validateForm()}).
 */
public class PurchasePanel extends JPanel {

    /** Alımda en sık kontrol edilen maddeler; tıklanınca ekspertiz notuna eklenir. */
    private static final String[] CHECKS = {
            "Ekran sağlam", "Kasa temiz", "Tuşlar çalışıyor", "Kamera sağlam", "Şarj oluyor",
            "Parmak izi / Face ID çalışıyor", "Hesap çıkışı yapıldı (iCloud/Google)", "IMEI kayıtlı",
            "Faturası var", "Kutusu var", "Batarya sağlığı: %"
    };

    private final CustomerSelectBox sellerCombo;
    private final JLabel sellerError = FormKit.errorLabel();
    private final CustomerSummaryPanel sellerSummary = new CustomerSummaryPanel();
    private final DeviceFormPanel deviceFormPanel;
    private final CurrencyField priceField;
    private final JLabel priceError = FormKit.errorLabel();
    private final DatePicker datePicker;
    private final JTextArea expertiseNotesArea;
    private int sellerToken;

    public PurchasePanel(ActionListener onNewCustomerRequested) {
        setLayout(new MigLayout("insets 16 20 12 20, fillx, width 940:940:",
                "[grow, fill, sg col]24[]24[grow, fill, sg col]", "[top]"));

        // --- Sol: satıcı ve cihaz ---
        JPanel left = column();
        left.add(sectionTitle("Satıcı"));
        sellerCombo = new CustomerSelectBox(onNewCustomerRequested);
        left.add(sellerCombo);
        left.add(sellerError, "gaptop 3");
        left.add(sellerSummary, "gaptop 8");

        left.add(sectionTitle("Cihaz"), "gaptop 22");
        deviceFormPanel = new DeviceFormPanel();
        left.add(deviceFormPanel);

        sellerCombo.setOnSelectionChanged(this::onSellerChanged);
        sellerCombo.setAfterUserChoice(deviceFormPanel::focusFirstField);

        // --- Sağ: alım ve ekspertiz ---
        JPanel right = column();
        right.setLayout(new MigLayout("insets 0, fillx, filly, wrap, hidemode 3", "[grow, fill]", "[][][][][][grow, fill]"));
        right.add(sectionTitle("Alım"));
        JPanel grid = FormKit.grid(2);
        priceField = new CurrencyField();
        // Alım kaydı yalnızca TL tutuyor; döviz seçeneği kur uygulanmadan kaydediliyordu.
        priceField.setAvailableCurrencies(List.of("TRY"));
        grid.add(FormKit.cell("Alım Fiyatı (TL) *", priceField, priceError));
        JFormattedTextField dateField = new JFormattedTextField();
        datePicker = new DatePicker();
        datePicker.setDateFormat("dd/MM/yyyy");
        datePicker.setEditor(dateField);
        datePicker.now();
        grid.add(FormKit.cell("Alım Tarihi", dateField, null));
        right.add(grid, "gaptop 4");
        priceField.addPropertyChangeListener("value", e -> {
            if (getPrice().signum() > 0) FormKit.clear(priceField, priceError);
        });

        right.add(sectionTitle("Ekspertiz"), "gaptop 22");
        right.add(FormKit.note("Kontrol ettiğiniz maddeleri ekleyin; \"Cihaz Ekspertizi\" belgesinde yer alır."),
                "gaptop 2, wmin 0");
        expertiseNotesArea = FormKit.textArea(5);
        expertiseNotesArea.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                "Cihazın alındığı andaki durumu, eksikleri, yapılan testler…");
        JPanel chips = WrapLayout.panel(6, 6);
        for (String check : CHECKS) {
            JButton chip = FormKit.chipButton(check);
            chip.setToolTipText("Ekspertiz notuna ekle");
            chip.addActionListener(e -> FormKit.appendPhrase(expertiseNotesArea, check));
            chips.add(chip);
        }
        right.add(chips, "wmin 0, gaptop 8, gapbottom 8");
        right.add(FormKit.areaScroll(expertiseNotesArea), "hmin 110");

        add(left, "wmin 0");
        add(new JSeparator(SwingConstants.VERTICAL), "growy");
        add(right, "wmin 0, growy");
    }

    private void onSellerChanged(Customer seller) {
        int token = ++sellerToken;
        sellerSummary.setCustomer(seller);
        deviceFormPanel.setContextCustomer(seller);
        if (seller == null) {
            deviceFormPanel.setSuggestedDevices(Collections.emptyList());
            return;
        }
        FormKit.clear(null, sellerError);
        sellerCombo.setError(false);
        ServiceManager.getDeviceService().getAllByCustomerId(seller.getId())
                .thenAccept(devices -> SwingUtilities.invokeLater(() -> {
                    if (token != sellerToken) return;
                    deviceFormPanel.setSuggestedDevices(devices);
                    sellerSummary.setDeviceCount(devices != null ? devices.size() : 0);
                }))
                .exceptionally(ex -> {
                    Servicio.getLogger().error("Satıcının cihazları yüklenemedi", ex);
                    return null;
                });
    }

    /**
     * Zorunlu alanları (satıcı, cihaz türü/marka/model, fiyat) denetler ve hataları alanların
     * altında gösterir; ilk hatalı alana odaklanır.
     *
     * @return form kaydedilebilir durumdaysa true
     */
    public boolean validateForm() {
        JComponent first = null;
        if (getSeller() == null) {
            sellerCombo.setError(true);
            FormKit.fail(new JLabel(), sellerError, "Cihazı satan müşteriyi seçin ya da yeni müşteri ekleyin.");
            first = sellerCombo;
        }
        JComponent device = deviceFormPanel.validateRequired();
        if (first == null) first = device;
        if (getPrice().signum() <= 0) {
            JComponent c = FormKit.fail(priceField, priceError, "Alım fiyatını girin.");
            if (first == null) first = c;
        }
        if (first != null) {
            first.requestFocusInWindow();
            return false;
        }
        return true;
    }

    public void setCustomers(List<Customer> customers) {
        sellerCombo.setCustomers(customers);
    }

    public void appendNewCustomer(Customer customer) {
        sellerCombo.appendCustomer(customer);
        SwingUtilities.invokeLater(deviceFormPanel::focusFirstField);
    }

    public Customer getSeller() {
        return sellerCombo.getSelectedItem();
    }

    public Device getDevice() {
        return deviceFormPanel.getDevice();
    }

    public BigDecimal getPrice() {
        return BigDecimal.valueOf(priceField.getDoubleValue());
    }

    public LocalDateTime getTransactionDate() {
        return datePicker.getSelectedDate() != null ? datePicker.getSelectedDate().atStartOfDay() : LocalDateTime.now();
    }

    public String getExpertiseNotes() {
        return expertiseNotesArea.getText().trim();
    }

    public void requestInitialFocus() {
        sellerCombo.grabFocus();
    }

    private static JPanel column() {
        JPanel p = new JPanel(new MigLayout("insets 0, fillx, wrap, hidemode 3", "[grow, fill]", ""));
        p.setOpaque(false);
        return p;
    }

    private static JLabel sectionTitle(String title) {
        JLabel label = new JLabel(title);
        label.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        return label;
    }
}

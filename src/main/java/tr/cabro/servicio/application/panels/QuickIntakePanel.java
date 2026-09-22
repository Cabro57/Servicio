package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.CustomerSelectBox;
import tr.cabro.servicio.application.component.CustomerSummaryPanel;
import tr.cabro.servicio.application.component.DeviceAccessField;
import tr.cabro.servicio.application.component.WrapLayout;
import tr.cabro.servicio.application.panels.edit.AbstractEditPanel;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.enums.DeviceAccessType;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Objects;

/**
 * Yeni servis kaydı oluşturmak veya mevcut bir kaydı düzenlemek için kullanılan panel.
 * <p>
 * Tezgâh akışına göre iki sütun: solda kim getirdi (müşteri) ve ne getirdi (cihaz), sağda cihaza
 * nasıl girilir (ekran kilidi) ve derdi ne (şikayet). Eski düzen 400px tek sütundu; müşteri
 * seçiminden sonra yalnızca "C-12" görünüyor, doğrulama hataları toast ile geliyordu.
 * <ul>
 *   <li>Müşteri seçilince altında bakiye, kayıtlı cihaz sayısı ve sorunlu müşteri uyarısı çıkar;
 *       müşterinin önceki cihazları cihaz bölümünde çip olarak listelenir.</li>
 *   <li>Cihaz alanlarına ilişkin mantık (IMEI arama, marka/tür yükleme, {@code currentDeviceId}
 *       takibi) {@link DeviceFormPanel}'e aittir.</li>
 *   <li>Hazır şikayet çipleri tıklanınca metne eklenir.</li>
 * </ul>
 * <p>
 * Yeni müşteri ekleme isteği, sabit bir eylem kodu yerine yapıcıda verilen
 * {@code onNewCustomerRequested} callback'i aracılığıyla iletilir; bu sayede
 * bu sınıf dışarıdaki modalın uygulama detaylarını bilmek zorunda kalmaz.
 * <p>
 * DİKKAT: {@link #initComponent()} üst sınıfın kurucusundan çağrılır — alanlara başlangıç
 * değeri ({@code = ...}) VERİLMEMELİ, aksi halde kurucu bittikten sonra sıfırlanırlar.
 */
public class QuickIntakePanel extends AbstractEditPanel<WorkOrder> {

    /** Tezgâhta en sık duyulan şikayetler; tıklanınca şikayet metnine eklenir. */
    private static final String[] COMMON_FAULTS = {
            "Ekran kırık", "Dokunmatik çalışmıyor", "Şarj olmuyor", "Batarya çabuk bitiyor",
            "Açılmıyor", "Sıvı teması", "Ses / mikrofon sorunu", "Kamera çalışmıyor",
            "Şebeke çekmiyor", "Donma / yavaşlama"
    };

    /**
     * Kullanıcı "Yeni Müşteri Ekle" butonuna tıkladığında tetiklenir.
     * Yeni müşteri eklendiğinde {@link #appendNewCustomer(Customer)} çağrılmalıdır.
     */
    private final Runnable onNewCustomerRequested;

    private CustomerSelectBox customerCombo;
    private JLabel customerError;
    private CustomerSummaryPanel customerSummary;
    private DeviceFormPanel deviceFormPanel;
    private DeviceAccessField deviceAccessField;
    private JTextArea reportedFaultArea;
    private int customerLoadToken;

    // -------------------------------------------------------------------------
    // Yapıcılar
    // -------------------------------------------------------------------------

    /**
     * Yeni kayıt modu.
     *
     * @param onNewCustomerRequested yeni müşteri modalını açacak callback
     */
    public QuickIntakePanel(Runnable onNewCustomerRequested) {
        super(new WorkOrder());
        this.onNewCustomerRequested = onNewCustomerRequested;
        loadCustomers();
    }

    /**
     * Düzenleme modu.
     *
     * @param data                   düzenlenecek servis kaydı
     * @param onNewCustomerRequested yeni müşteri modalını açacak callback
     */
    public QuickIntakePanel(WorkOrder data, Runnable onNewCustomerRequested) {
        super(data);
        this.onNewCustomerRequested = onNewCustomerRequested;
        loadCustomers();
    }

    // -------------------------------------------------------------------------
    // AbstractEditPanel implementasyonu
    // -------------------------------------------------------------------------

    @Override
    protected void initComponent() {
        setLayout(new BorderLayout());

        JPanel form = new JPanel(new MigLayout(
                "insets 16 20 12 20, fillx, width 940:940:",
                "[grow, fill, sg col]24[]24[grow, fill, sg col]", "[top]"));

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        form.add(buildLeftColumn(), "wmin 0");
        form.add(new JSeparator(SwingConstants.VERTICAL), "growy");
        form.add(buildRightColumn(), "wmin 0, growy");
    }

    private JPanel buildLeftColumn() {
        JPanel col = column();

        // --- Müşteri ---
        col.add(sectionTitle("Müşteri"));
        customerCombo = new CustomerSelectBox(e -> {
            if (onNewCustomerRequested != null) {
                onNewCustomerRequested.run();
            }
        });
        col.add(customerCombo);
        customerError = errorLabel();
        col.add(customerError, "gaptop 3");
        customerSummary = new CustomerSummaryPanel();
        col.add(customerSummary, "gaptop 8");

        // --- Cihaz ---
        col.add(sectionTitle("Cihaz"), "gaptop 22");
        deviceFormPanel = new DeviceFormPanel();
        col.add(deviceFormPanel);

        customerCombo.setOnSelectionChanged(this::onCustomerChanged);
        // Müşteri seçildikten sonra tezgâh akışında sıradaki iş cihazı okutmak.
        customerCombo.setAfterUserChoice(deviceFormPanel::focusFirstField);
        return col;
    }

    private JPanel buildRightColumn() {
        JPanel col = column();
        col.setLayout(new MigLayout("insets 0, fillx, filly, wrap, hidemode 3", "[grow, fill]", "[][][][][][grow, fill]"));

        // --- Ekran kilidi ---
        col.add(sectionTitle("Ekran Kilidi"));
        col.add(hint("Arıza tespiti ve testler için gerekir. Şifreli saklanır."), "gaptop 2, gapbottom 8");
        deviceAccessField = new DeviceAccessField();
        col.add(deviceAccessField);

        // --- Şikayet ---
        col.add(sectionTitle("Şikayet / Arıza"), "gaptop 22");
        JPanel chips = WrapLayout.panel(6, 6);
        for (String fault : COMMON_FAULTS) {
            JButton chip = new JButton(fault);
            chip.putClientProperty(FlatClientProperties.STYLE, "arc: 999; margin: 2,10,2,10; font: -1; focusWidth: 0");
            chip.setToolTipText("Şikayete ekle");
            chip.addActionListener(e -> appendFault(fault));
            chips.add(chip);
        }
        col.add(chips, "wmin 0, gaptop 2, gapbottom 8");

        reportedFaultArea = new JTextArea(5, 20);
        reportedFaultArea.setWrapStyleWord(true);
        reportedFaultArea.setLineWrap(true);
        reportedFaultArea.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                "Müşterinin anlattığı sorun: ne zaman başladı, düşme/sıvı teması oldu mu…");
        // Tab metne sekme eklemek yerine sonraki alana geçsin. Ctrl+Enter ile kaydetme
        // AbstractEditPanel'de kurulur ve bu alanda da çalışır.
        reportedFaultArea.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        reportedFaultArea.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
        JScrollPane faultScroll = new JScrollPane(reportedFaultArea);
        faultScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        col.add(faultScroll, "hmin 110");
        return col;
    }

    private void onCustomerChanged(Customer customer) {
        int token = ++customerLoadToken;
        deviceFormPanel.setContextCustomer(customer);
        customerSummary.setCustomer(customer);
        if (customer == null) {
            deviceFormPanel.setSuggestedDevices(Collections.emptyList());
            return;
        }
        hideCustomerError();

        ServiceManager.getDeviceService().getAllByCustomerId(customer.getId())
                .thenAccept(devices -> SwingUtilities.invokeLater(() -> {
                    if (token != customerLoadToken) return;
                    deviceFormPanel.setSuggestedDevices(devices);
                    customerSummary.setDeviceCount(devices != null ? devices.size() : 0);
                }))
                .exceptionally(ex -> {
                    Servicio.getLogger().error("Müşteri cihazları yüklenemedi", ex);
                    return null;
                });
    }

    private void appendFault(String fault) {
        String current = reportedFaultArea.getText().trim();
        if (current.toLowerCase().contains(fault.toLowerCase())) {
            reportedFaultArea.requestFocusInWindow();
            return;
        }
        reportedFaultArea.setText(current.isEmpty() ? fault : current + (current.endsWith(".") ? " " : ", ") + fault);
        reportedFaultArea.requestFocusInWindow();
        reportedFaultArea.setCaretPosition(reportedFaultArea.getDocument().getLength());
    }

    @Override
    protected boolean validateForm() {
        JComponent firstInvalid = null;

        if (customerCombo.getSelectedItem() == null) {
            customerCombo.setError(true);
            customerError.setText("Servis kaydı için müşteri seçin ya da yeni müşteri ekleyin.");
            customerError.setVisible(true);
            firstInvalid = customerCombo;
        }

        JComponent deviceInvalid = deviceFormPanel.validateRequired();
        if (firstInvalid == null) firstInvalid = deviceInvalid;

        revalidate();
        if (firstInvalid != null) {
            firstInvalid.requestFocusInWindow();
            return false;
        }
        return true;
    }

    private void hideCustomerError() {
        customerCombo.setError(false);
        customerError.setVisible(false);
    }

    @Override
    protected WorkOrder collectFormData(WorkOrder data) {
        Customer customer = customerCombo.getSelectedItem();
        Device device = deviceFormPanel.getDevice();

        data.setCustomer(customer);
        data.setCustomerId(customer.getId());
        data.setDevice(device);

        // Mevcut cihaz sistemde bulunduysa ID'yi bağla; yoksa null bırak (yeni kayıt)
        if (device.getId() != null) {
            data.setDeviceId(device.getId());
        } else {
            data.setDeviceId(null);
        }

        data.setReportedFault(reportedFaultArea.getText().trim());

        // Sadece yeni kayıtta oluşturma tarihi ve durum set edilir
        if (data.getId() == null || data.getId() == 0) {
            data.setCreatedAt(LocalDateTime.now());
            data.setServiceStatus(ServiceStatus.UNDER_REPAIR);
        }

        return data;
    }

    @Override
    protected void populateFormWith(WorkOrder data) {
        if (data == null) return;

        deviceFormPanel.setCurrentWorkOrderId(data.getId());
        if (data.getCustomer() != null) {
            customerCombo.setSelectedItem(data.getCustomer());
        }

        deviceFormPanel.setDevice(data.getDevice());

        reportedFaultArea.setText(data.getReportedFault() != null ? data.getReportedFault() : "");

        // Düzenleme modunda, bu servis kaydına ait mevcut erişim kodunu (varsa) getir
        deviceAccessField.clear();
        if (data.getId() != null) {
            ServiceManager.getDeviceAccessCredentialService().getActive(data.getId())
                    .thenAccept(credentialOpt -> SwingUtilities.invokeLater(() ->
                            credentialOpt.ifPresent(c -> deviceAccessField.setValue(c.getAccessType(), c.getSecret()))))
                    .exceptionally(ex -> {
                        Servicio.getLogger().error("Cihaz erişim kodu yüklenemedi", ex);
                        return null;
                    });
        }
    }

    @Override
    protected void clearForm() {
        customerCombo.setSelectedItem(null);
        deviceFormPanel.clear();
        deviceAccessField.clear();
        reportedFaultArea.setText("");
        hideCustomerError();
    }

    @Override
    protected WorkOrder createEmptyObject() {
        return new WorkOrder();
    }

    // -------------------------------------------------------------------------
    // Dışa açık yardımcılar
    // -------------------------------------------------------------------------

    /**
     * Yeni eklenen müşteriyi müşteri listesine ekler ve seçili hale getirir.
     * Yeni müşteri ekle modalı kapandıktan sonra çağrılmalıdır.
     */
    public void appendNewCustomer(Customer customer) {
        customerCombo.appendCustomer(customer);
        SwingUtilities.invokeLater(deviceFormPanel::focusFirstField);
    }

    /**
     * Panel açıldığında odağı doğru alana verir: müşteri önceden seçiliyse (müşteri sayfasından
     * açıldıysa) doğrudan IMEI alanına, değilse müşteri seçiciye.
     */
    public void requestInitialFocus() {
        if (customerCombo.getSelectedItem() != null) {
            deviceFormPanel.focusFirstField();
        } else {
            customerCombo.grabFocus();
        }
    }

    /** Girilen cihaz erişim türü — WorkOrder kaydedildikten sonra çağıran taraf bunu ayrı kaydeder. */
    public DeviceAccessType getDeviceAccessType() {
        return deviceAccessField.getAccessType();
    }

    /** Girilen cihaz erişim kodu (düz metin) — WorkOrder kaydedildikten sonra çağıran taraf bunu ayrı kaydeder. */
    public String getDeviceAccessSecret() {
        return deviceAccessField.getValue();
    }

    // -------------------------------------------------------------------------
    // Müşteri listesi yükleme
    // -------------------------------------------------------------------------

    private void loadCustomers() {
        ServiceManager.getCustomerService().getAll().thenAccept(customers -> {
            SwingUtilities.invokeLater(() -> {
                Customer currentSelection = customerCombo.getSelectedItem();
                customerCombo.setCustomers(customers);

                // Düzenleme modunda mevcut seçimi koru
                if (currentSelection != null) {
                    customers.stream()
                            .filter(c -> Objects.equals(c.getId(), currentSelection.getId()))
                            .findFirst()
                            .ifPresent(customerCombo::setSelectedItem);
                }
            });
        }).exceptionally(ex -> ErrorHandler.handle(this, "Müşteri listesi yüklenemedi", ex));
    }

    // -------------------------------------------------------------------------
    // Yardımcı
    // -------------------------------------------------------------------------

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

    private static JLabel hint(String text) {
        JLabel l = new JLabel(text);
        l.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -1");
        return l;
    }

    private static JLabel errorLabel() {
        JLabel l = new JLabel();
        l.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Servicio.dangerColor");
        l.setVisible(false);
        return l;
    }
}

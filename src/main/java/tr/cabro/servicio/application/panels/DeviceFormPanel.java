package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import org.jdesktop.swingx.autocomplete.AutoCompleteDecorator;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.WrapLayout;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.dictionary.DeviceBrand;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.model.enums.CustomerType;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.DeviceDictionaryManager;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Cihaz bilgilerini (IMEI/seri no, tür, marka, model, aksesuar) yöneten bağımsız panel.
 * Servis kaydı ve 2.el alım formlarında ortak kullanılır.
 * <p>
 * NOT: Ekran kilidi (PIN/şifre/desen) bilgisi burada değil, servis kaydına (WorkOrder)
 * bağlı olarak {@code DeviceAccessField} ile ayrıca toplanır — bkz. {@link QuickIntakePanel}.
 * <p>
 * IMEI/seri no alanı:
 * <ul>
 *   <li>15 haneli IMEI tamamlanınca ya da Enter'a basılınca sistemde kendiliğinden aranır;
 *       sonuç (kayıtlı cihaz, servis sayısı, son müşteri, açık servis) alanın altında yazılır.</li>
 *   <li>15 hanelik rakam dizisinin Luhn kontrol hanesi tutmuyorsa uyarı verir; kaydı engellemez
 *       (alan seri numarası da olabilir).</li>
 * </ul>
 * Seçili müşterinin daha önce getirdiği cihazlar alanın üstünde tıklanabilir çipler olarak durur.
 * <p>
 * Aksesuarlar {@link Device#getAccessory()} alanına virgülle ayrılmış düz metin olarak yazılır
 * ("Şarj aleti, Kılıf, Ekranda çizik"); eski serbest metin kayıtlar da aynı alanda açılır.
 */
public class DeviceFormPanel extends JPanel {

    /** Tezgâhta en sık teslim alınan aksesuarlar; metinde bu adlarla geçenler çip olarak işaretlenir. */
    private static final String[] ACCESSORIES = {"Şarj aleti", "Kablo", "Kılıf", "SIM kart", "Hafıza kartı", "Kutu"};
    private static final int VISIBLE_DEVICE_CHIPS = 3;

    // --- Alan bileşenleri ---
    private JComboBox<DeviceType> deviceTypeCombo;
    private JComboBox<DeviceBrand> brandCombo;
    private JTextField modelField;
    private JTextField serialNoField;
    private JTextField accessoryNoteField;
    private final Map<String, JToggleButton> accessoryChips = new LinkedHashMap<>();

    private JLabel serialStatus;
    private JLabel serialStatusDetail;
    private JLabel typeError;
    private JLabel brandError;
    private JLabel modelError;

    private JPanel previousDevicesRow;
    private JPanel previousDeviceChips;

    private DefaultComboBoxModel<DeviceType> deviceTypeModel;
    private DefaultComboBoxModel<DeviceBrand> brandModel;

    /**
     * Mevcut cihazın veritabanı ID'si. Null ise bu kayıt yeni bir cihazdır;
     * dolu ise güncelleme yapılacak demektir.
     */
    private Long currentDeviceId;

    private Device pendingDevice;

    /**
     * Populasyon sırasında (setDevice çağrıldığında) device_type_combo'nun
     * ActionListener'ının markaları boşaltmasını ve seri no aramasını engelleyen koruyucu bayrak.
     */
    private boolean isPopulating;

    private final DeviceDictionaryManager dictionaryManager;

    /** Seçili müşterinin daha önce sistemde kayıtlı cihazları. */
    private final List<Device> suggestedDevices = new ArrayList<>();

    /** Cihazın "başka müşteriye ait" uyarısı için karşılaştırılan müşteri. */
    private Customer contextCustomer;
    /** Düzenlenen servis kaydı; kendi kaydı "açık servis var" uyarısı vermesin. */
    private Long currentWorkOrderId;

    /**
     * true (varsayılan): IMEI araması cihaz bulursa form o cihazla doldurulur (servis kaydı akışı).
     * false: cihaz düzenlenirken bulunan başka bir kayıt formu ezmez, yalnızca uyarı verilir.
     */
    private boolean lookupFillsForm = true;

    private Timer autoSearchTimer;
    private String lastSearchedSerial;
    private int searchToken;

    public DeviceFormPanel() {
        this.dictionaryManager = ServiceManager.getDeviceDictionaryManager();
        initComponents();
        initEvents();
        loadDeviceTypes();
    }

    // -------------------------------------------------------------------------
    // Kurulum
    // -------------------------------------------------------------------------

    private void initComponents() {
        setLayout(new MigLayout("insets 0, fillx, wrap 2, hidemode 3, gapx 12, gapy 10",
                "[grow, fill, sg col][grow, fill, sg col]", "[top]"));
        setOpaque(false);

        // --- Müşterinin önceki cihazları ---
        previousDeviceChips = WrapLayout.panel(6, 6);
        JLabel previousLabel = new JLabel("Müşterinin önceki cihazları");
        previousLabel.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        // Etiket üstte, çipler tam genişlikte: çipler satıra sığmayınca alta kırılabilsin.
        previousDevicesRow = new JPanel(new MigLayout("insets 0, gap 0, wrap, fillx", "[grow, fill]", "[]4[]"));
        previousDevicesRow.setOpaque(false);
        previousDevicesRow.add(previousLabel);
        previousDevicesRow.add(previousDeviceChips, "wmin 0");
        previousDevicesRow.setVisible(false);
        add(previousDevicesRow, "span 2");

        // --- IMEI / Seri No ---
        serialNoField = buildClearableField();
        serialNoField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "IMEI'yi okutun veya yazın, Enter ile arayın");
        JButton btnSearch = new JButton(new Ikon("icons/search.svg", 0.75f));
        btnSearch.setToolTipText("Sistemde ara (Enter)");
        btnSearch.getAccessibleContext().setAccessibleName("Seri numarasına göre cihaz ara");
        btnSearch.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        btnSearch.addActionListener(e -> searchBySerialNo(true));
        serialNoField.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, btnSearch);
        serialStatus = new JLabel();
        serialStatus.setIconTextGap(6);
        serialStatus.setVisible(false);
        // Durumun ayrıntısı (tür, servis sayısı, son getiren) ikinci satırda; tek satırda kesiliyordu.
        serialStatusDetail = new JLabel();
        serialStatusDetail.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        serialStatusDetail.setBorder(BorderFactory.createEmptyBorder(1, 18, 0, 0));
        serialStatusDetail.setVisible(false);
        JPanel status = new JPanel(new MigLayout("insets 0, gap 0, wrap, fillx, hidemode 3", "[grow, fill]", "[][]"));
        status.setOpaque(false);
        status.add(serialStatus, "wmin 0");
        status.add(serialStatusDetail, "wmin 0");
        add(cell("IMEI / Seri No", serialNoField, status), "span 2");

        // --- Tür / Marka / Model ---
        deviceTypeModel = new DefaultComboBoxModel<>();
        brandModel = new DefaultComboBoxModel<>();
        deviceTypeCombo = new JComboBox<>(deviceTypeModel);
        AutoCompleteDecorator.decorate(deviceTypeCombo);
        brandCombo = new JComboBox<>(brandModel);
        AutoCompleteDecorator.decorate(brandCombo);
        deviceTypeCombo.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Tür seçin");
        brandCombo.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Önce türü seçin");
        modelField = buildClearableField();
        modelField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Örn. Galaxy A52, iPhone 11");

        typeError = errorLabel();
        brandError = errorLabel();
        modelError = errorLabel();
        add(cell("Cihaz Türü *", deviceTypeCombo, typeError));
        add(cell("Marka *", brandCombo, brandError));
        add(cell("Model *", modelField, modelError), "span 2");

        // --- Aksesuar / Kozmetik ---
        JPanel chips = WrapLayout.panel(6, 6);
        for (String name : ACCESSORIES) {
            JToggleButton chip = chip(name);
            accessoryChips.put(name, chip);
            chips.add(chip);
        }
        accessoryNoteField = buildClearableField();
        accessoryNoteField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Kozmetik durum ve diğer notlar (örn. arka kapak çizik)");
        JPanel accessoryBlock = new JPanel(new MigLayout("insets 0, gap 0, wrap, fillx", "[grow, fill]", "[]6[]"));
        accessoryBlock.setOpaque(false);
        accessoryBlock.add(chips, "wmin 0");
        accessoryBlock.add(accessoryNoteField);
        add(cell("Teslim Alınanlar / Kozmetik", accessoryBlock, null), "span 2");
    }

    private void initEvents() {
        deviceTypeCombo.addActionListener(e -> {
            // Populasyon sırasında bu event tetiklenir ama marka listesini boşaltmamamız gerekir.
            if (isPopulating) return;
            DeviceType selected = (DeviceType) deviceTypeCombo.getSelectedItem();
            loadBrands(selected, null);
            if (selected != null) hideError(deviceTypeCombo, typeError);
        });
        brandCombo.addActionListener(e -> {
            if (brandCombo.getSelectedItem() != null) hideError(brandCombo, brandError);
        });
        watchError(modelField, modelError);

        // 15 haneli IMEI tamamlanınca kısa bir beklemeden sonra kendiliğinden ara (barkod okuyucu da
        // karakterleri tek tek gönderir; her karakterde sorgu atılmasın).
        autoSearchTimer = new Timer(300, e -> searchBySerialNo(false));
        autoSearchTimer.setRepeats(false);
        serialNoField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { onSerialTextChanged(); }
            public void removeUpdate(DocumentEvent e) { onSerialTextChanged(); }
            public void changedUpdate(DocumentEvent e) { }
        });
        // Enter seri no alanında aramayı tetikler (Ctrl+Enter modalin kaydet kısayolu olarak kalır).
        serialNoField.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "servicio.searchSerial");
        serialNoField.getActionMap().put("servicio.searchSerial", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                searchBySerialNo(true);
            }
        });
    }

    private void onSerialTextChanged() {
        if (isPopulating) return; // applyDevice() alanları doldururken tetiklenmesin
        String serial = serialNoField.getText().trim();
        if (serial.isEmpty()) {
            showStatus(null, null, null);
            autoSearchTimer.stop();
            lastSearchedSerial = null;
            return;
        }
        if (serial.matches("\\d{15}")) {
            autoSearchTimer.restart();
        } else {
            autoSearchTimer.stop();
            // Önceki aramanın sonucu artık bu metne ait değil.
            if (!serial.equals(lastSearchedSerial)) showStatus(null, null, null);
        }
    }

    // -------------------------------------------------------------------------
    // Seri no ile cihaz arama
    // -------------------------------------------------------------------------

    /**
     * @param explicit kullanıcı Enter/düğme ile istediyse true: aynı metin yeniden aranır ve
     *                 boş alan için uyarı gösterilir. Otomatik aramada aynı metin tekrar sorgulanmaz.
     */
    private void searchBySerialNo(boolean explicit) {
        String serial = serialNoField.getText().trim();
        if (serial.isEmpty()) {
            if (explicit) showStatus("icons/info.svg", "Aramak için IMEI veya seri numarası girin.", "$Label.disabledForeground");
            return;
        }
        if (!explicit && serial.equals(lastSearchedSerial)) return;
        lastSearchedSerial = serial;
        int token = ++searchToken;
        showStatus("icons/loader.svg", "Sistemde aranıyor…", "$Label.disabledForeground");

        ServiceManager.getDeviceService().getBySerialNo(serial).thenAccept(deviceOpt -> SwingUtilities.invokeLater(() -> {
            if (token != searchToken) return;
            if (deviceOpt.isPresent() && !lookupFillsForm) {
                Device device = deviceOpt.get();
                if (Objects.equals(device.getId(), currentDeviceId)) {
                    showStatus(null, null, null);
                } else {
                    showStatus("icons/triangle-alert.svg", "Bu IMEI/seri no başka bir cihaz kaydında var",
                            describe(device), "$Servicio.warningColor");
                }
            } else if (deviceOpt.isPresent()) {
                Device device = deviceOpt.get();
                applyDevice(device);
                showStatus("icons/circle-check.svg", "Sistemde kayıtlı: " + describe(device) + ". Bilgiler dolduruldu.",
                        "$Servicio.successColor");
                loadDeviceHistory(device, token);
            } else if (serial.matches("\\d{15}") && !isValidImei(serial)) {
                showStatus("icons/triangle-alert.svg",
                        "Sistemde yok. IMEI kontrol hanesi tutmuyor; rakamları tekrar kontrol edin.", "$Servicio.warningColor");
            } else {
                showStatus("icons/circle-dashed.svg", "Sistemde kayıtlı değil; yeni cihaz olarak kaydedilecek.",
                        "$Label.disabledForeground");
            }
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                if (token == searchToken) {
                    showStatus("icons/circle-x.svg", "Arama yapılamadı; bilgileri elle girebilirsiniz.", "$Servicio.dangerColor");
                }
            });
            Servicio.getLogger().error("Seri no araması başarısız", ex);
            return null;
        });
    }

    /**
     * Bulunan cihazın servis geçmişini özetler: kaç kez geldiği, son kimin getirdiği ve hâlâ açık
     * bir servisi olup olmadığı. Açık servis veya başka müşteri uyarı rengiyle yazılır — aynı cihaz
     * için ikinci kayıt açılmasını ya da yanlış müşteriye bağlanmasını önlemek için.
     */
    private void loadDeviceHistory(Device device, int token) {
        if (device.getId() == null) return;
        ServiceManager.getWorkOrderService().getAllByDevice(device.getId()).thenAccept(orders -> SwingUtilities.invokeLater(() -> {
            if (token != searchToken || orders == null || orders.isEmpty()) return;

            WorkOrder open = orders.stream()
                    .filter(o -> !Objects.equals(o.getId(), currentWorkOrderId))
                    .filter(o -> isOpen(o.getServiceStatus()))
                    .findFirst().orElse(null);
            Customer lastOwner = orders.get(0).getCustomer();

            StringBuilder detail = new StringBuilder(describe(device)).append(" · ").append(orders.size()).append(" servis kaydı");
            if (lastOwner != null) detail.append(" · son getiren: ").append(customerName(lastOwner));

            boolean otherOwner = contextCustomer != null && lastOwner != null
                    && !Objects.equals(contextCustomer.getId(), lastOwner.getId());
            if (open != null) {
                showStatus("icons/triangle-alert.svg", "Bu cihazın açık servisi var: SRV-" + open.getId() + " ("
                        + open.getServiceStatus().getDisplayName() + ")", detail.toString(), "$Servicio.warningColor");
            } else if (otherOwner) {
                showStatus("icons/triangle-alert.svg", "Cihaz başka bir müşteriye kayıtlı", detail.toString(), "$Servicio.warningColor");
            } else {
                showStatus("icons/circle-check.svg", "Sistemde kayıtlı, bilgiler dolduruldu", detail.toString(), "$Servicio.successColor");
            }
        })).exceptionally(ex -> {
            Servicio.getLogger().error("Cihaz servis geçmişi yüklenemedi", ex);
            return null;
        });
    }

    private static boolean isOpen(ServiceStatus status) {
        return status != null && status != ServiceStatus.DELIVERED && status != ServiceStatus.RETURN;
    }

    /** IMEI'nin son hanesi Luhn algoritmasıyla hesaplanan kontrol hanesidir. */
    static boolean isValidImei(String imei) {
        if (imei == null || !imei.matches("\\d{15}")) return false;
        int sum = 0;
        for (int i = 0; i < 15; i++) {
            int d = imei.charAt(14 - i) - '0';
            if (i % 2 == 1) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
        }
        return sum % 10 == 0;
    }

    private void showStatus(String icon, String text, String colorKey) {
        showStatus(icon, text, null, colorKey);
    }

    private void showStatus(String icon, String text, String detail, String colorKey) {
        serialStatusDetail.setText(detail);
        serialStatusDetail.setToolTipText(detail);
        serialStatusDetail.setVisible(text != null && detail != null);
        if (text == null) {
            serialStatus.setVisible(false);
        } else {
            serialStatus.setText(text);
            serialStatus.setToolTipText(text);
            serialStatus.setIcon(icon != null ? new Ikon(icon, 0.75f, colorKey.startsWith("$") ? colorKey.substring(1) : colorKey) : null);
            serialStatus.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: " + colorKey);
            serialStatus.setVisible(true);
        }
        revalidate();
        repaint();
    }

    // -------------------------------------------------------------------------
    // Müşteriye özel cihaz önerileri
    // -------------------------------------------------------------------------

    /** Seçili müşterinin daha önce sistemde kayıtlı cihazlarını çip olarak gösterir. */
    public void setSuggestedDevices(List<Device> devices) {
        suggestedDevices.clear();
        if (devices != null) suggestedDevices.addAll(devices);

        previousDeviceChips.removeAll();
        int shown = Math.min(VISIBLE_DEVICE_CHIPS, suggestedDevices.size());
        for (int i = 0; i < shown; i++) {
            Device d = suggestedDevices.get(i);
            JButton chip = deviceChip(shortName(d), d);
            previousDeviceChips.add(chip);
        }
        if (suggestedDevices.size() > shown) {
            JButton more = pillButton("+" + (suggestedDevices.size() - shown));
            more.setToolTipText("Diğer cihazlar");
            more.addActionListener(e -> {
                JPopupMenu menu = new JPopupMenu();
                for (Device d : suggestedDevices.subList(shown, suggestedDevices.size())) {
                    JMenuItem item = new JMenuItem(shortName(d) + (d.getSerialNo() != null ? "   " + d.getSerialNo() : ""));
                    item.addActionListener(ev -> chooseSuggested(d));
                    menu.add(item);
                }
                menu.show(more, 0, more.getHeight() + 2);
            });
            previousDeviceChips.add(more);
        }
        previousDevicesRow.setVisible(!suggestedDevices.isEmpty());
        revalidate();
        repaint();
    }

    private JButton deviceChip(String text, Device d) {
        JButton chip = pillButton(text);
        chip.setIcon(new Ikon("icons/tablet-smartphone.svg", 0.7f));
        chip.setToolTipText(d.getSerialNo() != null ? "IMEI/Seri: " + d.getSerialNo() : "Seri numarası kayıtlı değil");
        chip.addActionListener(e -> chooseSuggested(d));
        return chip;
    }

    private void chooseSuggested(Device d) {
        applyDevice(d);
        lastSearchedSerial = d.getSerialNo();
        showStatus("icons/circle-check.svg", "Müşterinin önceki cihazı seçildi: " + describe(d) + ".", "$Servicio.successColor");
        loadDeviceHistory(d, ++searchToken);
    }

    // -------------------------------------------------------------------------
    // Veri yükleme
    // -------------------------------------------------------------------------

    private void loadDeviceTypes() {
        dictionaryManager.getAllTypes().thenAccept(types -> SwingUtilities.invokeLater(() -> {
            // DefaultComboBoxModel ilk eklenen öğeyi kendiliğinden seçer; tür boş başlasın diye
            // ekleme sırasında olaylar bastırılır ve seçim temizlenir. Operatör türü bilerek seçmeli,
            // aksi halde listenin ilk türü (örn. "Akıllı Saat") fark edilmeden kayda geçiyordu.
            isPopulating = true;
            try {
                deviceTypeModel.removeAllElements();
                types.forEach(deviceTypeModel::addElement);
                deviceTypeCombo.setSelectedItem(null);
            } finally {
                isPopulating = false;
            }

            // Türler yüklendi — bekleyen device varsa şimdi uygula
            if (pendingDevice != null) {
                applyDevice(pendingDevice);
                pendingDevice = null;
            }
        })).exceptionally(ex -> {
            Servicio.getLogger().error("Cihaz türleri yüklenemedi", ex);
            return null;
        });
    }

    /**
     * Seçili türe göre marka listesini yükler.
     *
     * @param type           Filtrelenecek cihaz türü; null ise liste temizlenir.
     * @param brandToSelect  Yükleme tamamlandığında seçilecek marka; null ise ilk eleman seçilir.
     */
    private void loadBrands(DeviceType type, DeviceBrand brandToSelect) {
        brandModel.removeAllElements();
        if (type == null) return;

        dictionaryManager.getBrandsByTypeId(type.getId()).thenAccept(brands -> {
            SwingUtilities.invokeLater(() -> {
                brands.forEach(brandModel::addElement);
                if (brandToSelect != null) {
                    brands.stream()
                            .filter(b -> b.getId().equals(brandToSelect.getId()))
                            .findFirst()
                            .ifPresent(brandCombo::setSelectedItem);
                }
            });
        }).exceptionally(ex -> {
            Servicio.getLogger().error("Markalar yüklenemedi", ex);
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Dışa açık API
    // -------------------------------------------------------------------------

    /**
     * Formu verilen cihaz bilgileriyle doldurur.
     * Null geçilirse form sıfırlanır (yeni kayıt modu).
     */
    public void setDevice(Device device) {
        if (device == null) { clear(); return; }

        // Türler henüz yüklenmediyse beklet
        if (deviceTypeModel.getSize() == 0) {
            pendingDevice = device;
            return;
        }
        applyDevice(device);
    }

    /** Bkz. {@link #lookupFillsForm}; cihaz düzenleme formu false verir. */
    public void setLookupFillsForm(boolean lookupFillsForm) {
        this.lookupFillsForm = lookupFillsForm;
    }

    /** "Başka müşteriye kayıtlı" uyarısı için seçili müşteri. */
    public void setContextCustomer(Customer customer) {
        this.contextCustomer = customer;
    }

    /** Düzenleme modunda servis kaydının kendisi; "açık servisi var" uyarısından hariç tutulur. */
    public void setCurrentWorkOrderId(Long workOrderId) {
        this.currentWorkOrderId = workOrderId;
    }

    private void applyDevice(Device device) {
        isPopulating = true;
        try {
            currentDeviceId = device.getId();
            serialNoField.setText(nullToEmpty(device.getSerialNo()));
            modelField.setText(nullToEmpty(device.getModel()));
            setAccessoryText(device.getAccessory());

            // ID bazlı tür eşleştir
            DeviceType matchedType = null;
            if (device.getDeviceType() != null) {
                for (int i = 0; i < deviceTypeModel.getSize(); i++) {
                    DeviceType t = deviceTypeModel.getElementAt(i);
                    if (t.getId().equals(device.getDeviceType().getId())) {
                        matchedType = t;
                        break;
                    }
                }
            }
            deviceTypeCombo.setSelectedItem(matchedType);
            loadBrands(matchedType, device.getBrand());
            clearErrors();
        } finally {
            isPopulating = false;
        }
    }

    /**
     * Form alanlarından bir {@link Device} nesnesi oluşturur.
     * Zorunlu alanlar (tür, marka, model) boşsa null döner.
     */
    public Device getDevice() {
        DeviceType type = (DeviceType) deviceTypeCombo.getSelectedItem();
        DeviceBrand brand = (DeviceBrand) brandCombo.getSelectedItem();
        String model = modelField.getText().trim();

        if (type == null || brand == null || model.isEmpty()) {
            return null;
        }

        Device device = new Device();
        device.setId(currentDeviceId);
        device.setDeviceType(type);
        device.setBrand(brand);
        device.setModel(model);
        String serialNo = serialNoField.getText().trim();
        if (!serialNo.isEmpty()) {
            device.setSerialNo(serialNo);
        }
        device.setAccessory(getAccessoryText());
        return device;
    }

    /**
     * Zorunlu alanları (tür, marka, model) işaretler ve mesajlarını alanların altında gösterir.
     *
     * @return ilk eksik alan; hepsi doluysa null
     */
    public JComponent validateRequired() {
        clearErrors();
        JComponent first = null;
        if (deviceTypeCombo.getSelectedItem() == null) {
            first = showError(deviceTypeCombo, typeError, "Cihaz türünü seçin.");
        }
        if (brandCombo.getSelectedItem() == null) {
            JComponent c = showError(brandCombo, brandError,
                    deviceTypeCombo.getSelectedItem() == null ? "Önce türü seçin." : "Markayı seçin.");
            if (first == null) first = c;
        }
        if (modelField.getText().trim().isEmpty()) {
            JComponent c = showError(modelField, modelError, "Modeli yazın.");
            if (first == null) first = c;
        }
        return first;
    }

    /** Formda ilk doldurulacak alan: IMEI/seri no. */
    public void focusFirstField() {
        serialNoField.requestFocusInWindow();
    }

    /**
     * Tüm alanları ve iç durumu sıfırlar.
     */
    public void clear() {
        isPopulating = true;
        try {
            currentDeviceId = null;
            deviceTypeCombo.setSelectedItem(null);
            brandModel.removeAllElements();
            modelField.setText("");
            serialNoField.setText("");
            setAccessoryText(null);
            lastSearchedSerial = null;
            clearErrors();
            showStatus(null, null, null);
        } finally {
            isPopulating = false;
        }
        setSuggestedDevices(null);
    }

    // -------------------------------------------------------------------------
    // Aksesuar metni <-> çipler
    // -------------------------------------------------------------------------

    private String getAccessoryText() {
        List<String> parts = new ArrayList<>();
        accessoryChips.forEach((name, chip) -> { if (chip.isSelected()) parts.add(name); });
        String note = accessoryNoteField.getText().trim();
        if (!note.isEmpty()) parts.add(note);
        return String.join(", ", parts);
    }

    /** Metindeki bilinen aksesuar adları çip olarak işaretlenir, kalan kısım not alanına yazılır. */
    private void setAccessoryText(String text) {
        accessoryChips.values().forEach(c -> c.setSelected(false));
        List<String> rest = new ArrayList<>();
        if (text != null && !text.isBlank()) {
            for (String part : text.split(",")) {
                String token = part.trim();
                if (token.isEmpty()) continue;
                JToggleButton chip = accessoryChips.entrySet().stream()
                        .filter(e -> e.getKey().equalsIgnoreCase(token))
                        .map(Map.Entry::getValue).findFirst().orElse(null);
                if (chip != null) chip.setSelected(true); else rest.add(token);
            }
        }
        accessoryNoteField.setText(String.join(", ", rest));
    }

    // -------------------------------------------------------------------------
    // Yardımcı
    // -------------------------------------------------------------------------

    private JPanel cell(String label, JComponent input, JComponent below) {
        JPanel c = new JPanel(new MigLayout("insets 0, gap 0, wrap, fillx, hidemode 3", "[grow, fill]", "[]4[]3[]"));
        c.setOpaque(false);
        JLabel l = new JLabel(label);
        l.putClientProperty(FlatClientProperties.STYLE, "font: -1");
        l.setLabelFor(input);
        c.add(l);
        c.add(input);
        if (below != null) c.add(below, "wmin 0");
        return c;
    }

    private static JLabel errorLabel() {
        JLabel l = new JLabel();
        l.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Servicio.dangerColor");
        l.setVisible(false);
        return l;
    }

    private JComponent showError(JComponent field, JLabel label, String message) {
        field.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR);
        label.setText(message);
        label.setVisible(true);
        revalidate();
        return field;
    }

    private void hideError(JComponent field, JLabel label) {
        if (!label.isVisible()) return;
        field.putClientProperty(FlatClientProperties.OUTLINE, null);
        label.setVisible(false);
        revalidate();
    }

    private void clearErrors() {
        hideError(deviceTypeCombo, typeError);
        hideError(brandCombo, brandError);
        hideError(modelField, modelError);
    }

    private void watchError(JTextComponent field, JLabel label) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { hideError(field, label); }
            @Override public void removeUpdate(DocumentEvent e) { }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
    }

    /**
     * Aksesuar çipi. Seçiliyken vurgu rengiyle dolu ve başında onay işareti olur; açık tonlu
     * dolgu seçili/seçisiz farkını göstermiyordu. Renk tek başına yetmesin diye işaret de eklenir.
     */
    private static JToggleButton chip(String text) {
        JToggleButton b = new JToggleButton(text);
        b.putClientProperty(FlatClientProperties.STYLE,
                "arc: 999; margin: 2,10,2,10; font: -1; focusWidth: 0; iconTextGap: 4; "
                        + "selectedBackground: $Component.accentColor; "
                        + "selectedForeground: $Button.default.foreground; "
                        + "borderColor: $Component.borderColor");
        Icon check = new Ikon("icons/check-check.svg", 0.7f, "Button.default.foreground");
        b.addItemListener(e -> b.setIcon(b.isSelected() ? check : null));
        return b;
    }

    private static JButton pillButton(String text) {
        JButton b = new JButton(text);
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 999; margin: 2,10,2,10; font: -1; focusWidth: 0; iconTextGap: 4");
        return b;
    }

    private static String shortName(Device d) {
        String brand = d.getBrand() != null ? d.getBrand().getName() : "";
        String model = d.getModel() != null ? d.getModel() : "";
        String s = (brand + " " + model).trim();
        return s.isEmpty() ? "Cihaz #" + d.getId() : s;
    }

    private static String describe(Device d) {
        String type = d.getDeviceType() != null ? d.getDeviceType().getName() + " · " : "";
        return type + shortName(d);
    }

    private static String customerName(Customer c) {
        if (c.getType() == CustomerType.KURUMSAL && c.getBusinessName() != null && !c.getBusinessName().isBlank()) {
            return c.getBusinessName().trim();
        }
        return (nullToEmpty(c.getFirstName()) + " " + nullToEmpty(c.getLastName())).trim();
    }

    private JTextField buildClearableField() {
        JTextField field = new JTextField();
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        return field;
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}

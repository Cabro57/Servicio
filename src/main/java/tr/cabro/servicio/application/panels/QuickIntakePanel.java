    package tr.cabro.servicio.application.panels;

    import com.formdev.flatlaf.FlatClientProperties;
    import net.miginfocom.swing.MigLayout;
    import raven.modal.component.ModalBorderAction;
    import raven.modal.component.SimpleModalBorder;
    import tr.cabro.servicio.application.component.CustomerSelectBox;
    import tr.cabro.servicio.application.panels.edit.AbstractEditPanel;
    import raven.modal.Toast;
    import tr.cabro.servicio.model.Customer;
    import tr.cabro.servicio.model.Device;
    import tr.cabro.servicio.model.WorkOrder;
    import tr.cabro.servicio.model.enums.ServiceStatus;
    import tr.cabro.servicio.service.ServiceManager;

    import javax.swing.*;
    import java.awt.event.KeyAdapter;
    import java.awt.event.KeyEvent;
    import java.time.LocalDateTime;
    import java.util.Objects;

    /**
     * Yeni servis kaydı oluşturmak veya mevcut bir kaydı düzenlemek için kullanılan panel.
     * <p>
     * Sorumlulukları:
     * <ul>
     *   <li>Müşteri seçimini yönetmek ({@link CustomerSelectBox})</li>
     *   <li>Cihaz bilgilerini {@link DeviceFormPanel} aracılığıyla toplamak</li>
     *   <li>Müşteri şikayetini almak</li>
     *   <li>Form verilerini bir {@link WorkOrder} nesnesine dönüştürmek</li>
     * </ul>
     * <p>
     * Cihaz alanlarına ilişkin tüm mantık (marka/tür yükleme, seri no sorgulama,
     * {@code currentDeviceId} takibi) {@link DeviceFormPanel}'e devredilmiştir.
     * <p>
     * Yeni müşteri ekleme isteği, sabit bir eylem kodu yerine yapıcıda verilen
     * {@code onNewCustomerRequested} callback'i aracılığıyla iletilir; bu sayede
     * bu sınıf dışarıdaki modalın uygulama detaylarını bilmek zorunda kalmaz.
     */
    public class QuickIntakePanel extends AbstractEditPanel<WorkOrder> {

        /**
         * Kullanıcı "Yeni Müşteri Ekle" butonuna tıkladığında tetiklenir.
         * Yeni müşteri eklendiğinde {@link #appendNewCustomer(Customer)} çağrılmalıdır.
         */
        private final Runnable onNewCustomerRequested;

        private CustomerSelectBox customerCombo;
        private DeviceFormPanel deviceFormPanel;
        private JTextArea reportedFaultArea;

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
            setLayout(new MigLayout("fillx,wrap,insets 5 30 5 30,width 400", "[fill]", ""));

            // --- Müşteri ---
            customerCombo = new CustomerSelectBox(e -> {
                if (onNewCustomerRequested != null) {
                    onNewCustomerRequested.run();
                }
            });
            addSectionTitle("Müşteri");
            add(customerCombo, "growx");

            // --- Cihaz Bilgileri ---
            addSectionTitle("Cihaz Bilgileri");
            deviceFormPanel = new DeviceFormPanel();
            add(deviceFormPanel, "growx");

            // --- Müşteri Şikayeti ---
            addSectionTitle("Müşteri Şikayeti");
            reportedFaultArea = new JTextArea();
            reportedFaultArea.setWrapStyleWord(true);
            reportedFaultArea.setLineWrap(true);
            reportedFaultArea.addKeyListener(new KeyAdapter() {
                @Override
                public void keyTyped(KeyEvent e) {
                    // Ctrl+Enter → formu kaydet
                    if (e.isControlDown() && e.getKeyChar() == 10) {
                        ModalBorderAction action = ModalBorderAction.getModalBorderAction(QuickIntakePanel.this);
                        if (action != null) action.doAction(SimpleModalBorder.YES_OPTION);
                    }
                }
            });
            add(new JLabel("Şikayet / Arıza:"), "gapy 5 0");
            add(new JScrollPane(reportedFaultArea), "height 150,grow,pushy");
        }

        @Override
        protected boolean validateForm() {
            // Müşteri seçimi zorunlu
            if (customerCombo.getSelectedItem() == null) {
                showValidationError(Toast.Type.WARNING, "Lütfen bir müşteri seçin.");
                customerCombo.requestFocus();
                return false;
            }

            // Cihaz türü ve markası zorunlu
            if (deviceFormPanel.getDevice() == null) {
                showValidationError(Toast.Type.WARNING, "Marka ve model bilgileri girmek zorunludur.");
                return false;
            }

            return true;
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

            if (data.getCustomer() != null) {
                customerCombo.setSelectedItem(data.getCustomer());
            }

            deviceFormPanel.
                    setDevice(data.getDevice());

            reportedFaultArea.setText(data.getReportedFault() != null ? data.getReportedFault() : "");
        }

        @Override
        protected void clearForm() {
            customerCombo.setSelectedItem(null);
            deviceFormPanel.clear();
            reportedFaultArea.setText("");
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
        }

        /**
         * Panel açıldığında odağı doğru alana verir.
         */
        public void requestInitialFocus() {
            customerCombo.grabFocus();
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
            }).exceptionally(ex -> {
                SwingUtilities.invokeLater(() ->
                        Toast.show(this, Toast.Type.ERROR, "Müşteri listesi yüklenemedi."));
                return null;
            });
        }

        // -------------------------------------------------------------------------
        // Yardımcı
        // -------------------------------------------------------------------------

        private void addSectionTitle(String title) {
            JLabel label = new JLabel(title);
            label.putClientProperty(FlatClientProperties.STYLE, "font:+2");
            add(label, "gapy 5 0");
            add(new JSeparator(), "height 2!,gapy 0 0");
        }
    }
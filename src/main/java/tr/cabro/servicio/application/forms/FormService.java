package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import lombok.NonNull;
import net.miginfocom.swing.MigLayout;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import raven.modal.system.Form;
import raven.modal.system.FormManager;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.listeners.ServiceEditListener;
import tr.cabro.servicio.application.panels.ProcessSelectedPanel;
import tr.cabro.servicio.application.panels.edit.CustomerEditPanel;
import tr.cabro.servicio.application.panels.service.FaultProcessInfoPanel;
import tr.cabro.servicio.application.panels.service.PartsNotesInfoPanel;
import tr.cabro.servicio.application.panels.service.PriceInfoPanel;
import tr.cabro.servicio.application.panels.service.QuickIntakePanel;
import tr.cabro.servicio.application.util.Ikon;
import tr.cabro.servicio.model.AddedPart;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Process;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.RepairService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.settings.DeviceSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.time.format.DateTimeFormatter;

public class FormService extends Form {

    private Service service;
    private final RepairService repairService;

    // --- UI Bileşenleri (Üst ve Sol) ---
    private JLabel lblHeaderTitle;
    private JLabel lblHeaderSubtitle;
    private JLabel lblHeaderBadge;
    private JComboBox<ServiceStatus> statusComboBox;

    private JPanel leftColumn;
    private JPanel rightColumn;

    private JLabel lblCustomerName, lblCustomerPhone, lblCustomerEmail;
    private JLabel lblDeviceType, lblDeviceBrand, lblDeviceModel, lblDeviceSerial;
    private JTextArea txtReportedFault;
    private JLabel lblDateArrival, lblDateEstimated;

    // --- ESKİ İŞ MANTIĞI PANELLERİ (Sağ Kolon İçin) ---
    private FaultProcessInfoPanel fault_process_info;
    private PartsNotesInfoPanel part_notes_info;
    private PriceInfoPanel price_info;

    private Timer autoSaveTimer;

    public FormService(Service service) {
        this.repairService = ServiceManager.getRepairService();
        initComponent();
        initListeners();
        setService(service);

        autoSaveTimer = new Timer(1000, e -> forceSaveAsync());
        autoSaveTimer.setRepeats(false);

        // YENİ: UYGULAMA KAPATILIRSA (X Tuşu veya Alt+F4)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (autoSaveTimer != null && autoSaveTimer.isRunning()) {
                forceSaveSync(); // Uygulama kapanmadan son saniyede veriyi kurtar
            }
        }));
    }

    public void setService(@NonNull Service service) {
        this.service = service;
        if (service.getId() > 0) {
            // Asenkron veri çekme (Hydration) ve panelleri doldurma
            repairService.getServiceParts(service.getId()).thenAccept(parts -> {
                this.service.setAddedParts(parts);

                repairService.getTotalPartsCostForService(service.getId()).thenAccept(totalCost -> {
                    this.service.setTotalPartsCost(totalCost);

                    SwingUtilities.invokeLater(() -> {
                        hydrateReadOnlyUI(); // Sol kolonu doldur
                        bindOperationalPanels(); // Sağ kolon panellerini doldur
                    });
                });
            }).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, "Servis detayları yüklenemedi!"));
                return null;
            });
        }
    }

    private void bindOperationalPanels() {
        // Eski panellerin kendi içlerindeki veriyi (service) almasını sağlıyoruz.
        fault_process_info.bindService(this.service);
        part_notes_info.bindService(this.service);
        price_info.bindService(this.service);
    }

    private void hydrateReadOnlyUI() {
        lblHeaderTitle.setText("SRV-" + service.getId());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", new java.util.Locale("tr", "TR"));
        String dateStr = service.getCreatedAt() != null ? service.getCreatedAt().format(formatter) : "-";
        lblHeaderSubtitle.setText("Kayıt Tarihi: " + dateStr);

        ServiceStatus currentStatus = service.getServiceStatus() != null ? service.getServiceStatus() : ServiceStatus.UNDER_REPAIR;
        lblHeaderBadge.setText(currentStatus.getDisplayName());

        // Listener'ın boş yere tetiklenmemesi için geçici kapatıyoruz
        ActionListener[] listeners = statusComboBox.getActionListeners();
        for (ActionListener l : listeners) statusComboBox.removeActionListener(l);
        statusComboBox.setSelectedItem(currentStatus);
        for (ActionListener l : listeners) statusComboBox.addActionListener(l);

        if (service.getCustomer() != null) {
            lblCustomerName.setText(service.getCustomer().getName() + " " + service.getCustomer().getSurname());
            lblCustomerPhone.setText(service.getCustomer().getPhoneNumber1() != null ? service.getCustomer().getPhoneNumber1() : "-");
            lblCustomerEmail.setText(service.getCustomer().getEmail() != null ? service.getCustomer().getEmail() : "-");
        }

        lblDeviceType.setText(service.getDeviceType() != null ? service.getDeviceType() : "-");
        lblDeviceBrand.setText(service.getDeviceBrand() != null ? service.getDeviceBrand() : "-");
        lblDeviceModel.setText(service.getDeviceModel() != null ? service.getDeviceModel() : "-");
        lblDeviceSerial.setText(service.getDeviceSerial() != null ? service.getDeviceSerial() : "-");
        txtReportedFault.setText(service.getReportedFault() != null ? service.getReportedFault() : "Belirtilmemiş.");

        lblDateArrival.setText(dateStr);
        lblDateEstimated.setText(service.getCreatedAt() != null ? service.getCreatedAt().plusDays(2).format(formatter) : "-");
    }

    private void initListeners() {

        ServiceEditListener serviceEditListener = new ServiceEditListener() {
            @Override
            public void onPartChange(double price) {
                price_info.setMaterialCost(price);
                autoSaveService();
            }

            @Override
            public void onProcessAdded(String name, double price) {
                price_info.addLaborCost(price);
                fault_process_info.appendAction(name);
                autoSaveService();
            }

            @Override
            public void onProcessAdded(Process process) {
                fault_process_info.appendAction(process.getName());
                price_info.addLaborCost(process.getPrice());
                autoSaveService();
            }

            @Override
            public void onStatusChanged(String status) {}

            @Override
            public void onDataChanged() {
                autoSaveService();
            }

            @Override
            public void requestRefresh() {
                if (service != null && service.getId() > 0) setService(service);
            }

            @Override
            public void onPartAdded(AddedPart part) {
                autoSaveService();
            }
        };

        fault_process_info.setServicePanelListener(serviceEditListener);
        part_notes_info.setServicePanelListener(serviceEditListener);
        price_info.setServicePanelListener(serviceEditListener);

        fault_process_info.action_taken_button.addActionListener(e -> onActionTaken());
    }

    /**
     * İşlem yapıldıkça arkaplanda sessizce kaydeder
     */
    private void autoSaveService() {
        if (service == null || service.getId() <= 0) return;
        if (autoSaveTimer.isRunning()) autoSaveTimer.restart();
        else autoSaveTimer.start();
    }

    /**
     * Kullanıcı formdayken arkaplanda sessizce ve asenkron kaydeder. (Arayüzü dondurmaz)
     */
    private void forceSaveAsync() {
        if (service == null || service.getId() <= 0) return;
        collectDataForSave();
        repairService.save(service, true).exceptionally(ex -> {
            System.err.println("Otomatik Kayıt Hatası: " + ex.getMessage());
            return null;
        });
    }

    /**
     * Formdan ÇIKILIRKEN veya uygulama KAPATILIRKEN senkron (bloklayıcı) olarak kaydeder.
     * Veritabanı işlemi bitene kadar kapanmayı durdurur.
     */
    private void forceSaveSync() {
        if (service == null || service.getId() <= 0) return;
        collectDataForSave();
        try {
            // join() veya get() kullanarak asenkron işlemin BİTMESİNİ BEKLİYORUZ.
            repairService.save(service, true).join();
            System.out.println("Sistemden çıkılırken son veriler başarıyla kurtarıldı!");
        } catch (Exception e) {
            System.err.println("Çıkışta kayıt kurtarılamadı: " + e.getMessage());
        }
    }

    private void collectDataForSave() {
        service.setLaborCost(price_info.getLaborCost());
        service.setPaid(price_info.getPaid());
        service.setPaymentType(price_info.getPaymentType());
        service.setDetectedFault(fault_process_info.getDetectedFault());
        service.setActionTaken(fault_process_info.getActionTaken());
        service.setNotes(part_notes_info.getNotes());
    }

    private void initComponent() {
        setLayout(new MigLayout("fill, insets 20", "[grow]", "[pref!]20[grow, fill]"));

        // Panellerin init edilmesi
        fault_process_info = new FaultProcessInfoPanel();
        fault_process_info.setOpaque(false); // Kart tasarımıyla uyumlu olması için arkaplanı saydamlaştırdık

        part_notes_info = new PartsNotesInfoPanel();
        part_notes_info.setOpaque(false);

        price_info = new PriceInfoPanel();
        price_info.setOpaque(false);

        createHeaderPanel();
        createMainContentPanels();
    }

    private void createHeaderPanel() {
        JPanel headerPanel = new JPanel(new MigLayout("insets 0, fillx", "[][][][grow][][]", "[]"));
        headerPanel.setOpaque(false);

        JButton btnBack = new JButton(new Ikon("icons/arrow-left.svg", 0.5f));
        btnBack.putClientProperty(FlatClientProperties.STYLE, "arc: 999; background: lighten($Panel.background, 5%);");
        btnBack.addActionListener(e -> {
            FormManager.showForm(new FormServices());
        });

        JPanel titlePanel = new JPanel(new MigLayout("insets 0, gapy 2", "[fill]", "[][]"));
        titlePanel.setOpaque(false);
        lblHeaderTitle = new JLabel("SRV-YENI");
        lblHeaderTitle.putClientProperty(FlatClientProperties.STYLE, "font: bold +8");
        lblHeaderSubtitle = new JLabel("Kayıt Tarihi: -");
        lblHeaderSubtitle.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -1");
        titlePanel.add(lblHeaderTitle, "wrap");
        titlePanel.add(lblHeaderSubtitle);

        lblHeaderBadge = new JLabel("Bekliyor");
        lblHeaderBadge.putClientProperty(FlatClientProperties.STYLE, "background: #f1c40f; foreground: #000000; arc: 15; border: 4,10,4,10; font: bold -1");
        lblHeaderBadge.setOpaque(true);

        JPanel statusPanel = new JPanel(new MigLayout("insets 0", "[][]", "[]"));
        statusPanel.setOpaque(false);
        statusComboBox = new JComboBox<>(ServiceStatus.values());
        statusComboBox.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
        statusComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ServiceStatus) setText(((ServiceStatus) value).getDisplayName());
                return this;
            }
        });

        statusComboBox.addActionListener(e -> {
            ServiceStatus newStatus = (ServiceStatus) statusComboBox.getSelectedItem();
            if (newStatus != null && service != null && service.getId() > 0 && service.getServiceStatus() != newStatus) {
                lblHeaderBadge.setText(newStatus.getDisplayName());
                service.setServiceStatus(newStatus);
                repairService.save(service, true).thenAccept(s -> {
                    SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.SUCCESS, "Durum güncellendi."));
                });
            }
        });

        statusPanel.add(new JLabel("Durum Güncelle:"));
        statusPanel.add(statusComboBox);

        headerPanel.add(btnBack, "w 40!, h 40!, aligny top");
        headerPanel.add(titlePanel, "gapleft 15, aligny top");
        headerPanel.add(lblHeaderBadge, "gapleft 10, aligny top, gaptop 5");
        headerPanel.add(new JLabel(""), "growx, pushx");
        headerPanel.add(statusPanel, "align right, aligny top");

        add(headerPanel, "wrap, growx");
    }

    private void onActionTaken() {
        String selectedDeviceType = service.getDeviceType();

        if (selectedDeviceType == null || selectedDeviceType.isEmpty()) {
            Toast.show(this, Toast.Type.WARNING, "Lütfen bir cihaz türü seçin!");
            return;
        }

        ProcessSelectedPanel panel = new ProcessSelectedPanel();

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Tamam", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(panel, "İşlem Seç", options,
                (controller, action) -> {
                    if (action == SimpleModalBorder.OPENED) {
                        DeviceSettings settings = Servicio.getDeviceSettings();
                        java.util.List<Process> processes = settings.getProcesses(selectedDeviceType);
                        panel.setProcess(processes);

                        panel.setOnProcessDoubleClick(process -> {
                            price_info.addLaborCost(process.getPrice());
                            fault_process_info.appendAction(process.getName());
                            controller.close();
                        });

                    } else if (action == SimpleModalBorder.OK_OPTION) {
                        java.util.List<Process> selected = panel.getSelectedProcesses();
                        if (selected == null || selected.isEmpty()) {
                            Toast.show(FormService.this, Toast.Type.WARNING, "Lütfen en az bir işlem seçin!");
                            return;
                        }

                        for (Process p : selected) {
                            price_info.addLaborCost(p.getPrice());
                            fault_process_info.appendAction(p.getName());
                        }

                        Toast.show(FormService.this, Toast.Type.SUCCESS, selected.size() + " işlem eklendi!");
                    }
                }), "processSelected");
    }

    private void createMainContentPanels() {
        JPanel contentPanel = new JPanel(new MigLayout("insets 0, gapx 20", "[330!, fill][grow, fill]", "[grow, fill]"));
        contentPanel.setOpaque(false);

        leftColumn = new JPanel(new MigLayout("insets 0, gapy 20", "[fill, grow]", "[pref!][pref!][pref!]"));
        leftColumn.setOpaque(false);

        leftColumn.add(createCustomerCard(), "wrap");
        leftColumn.add(createDeviceCard(), "wrap");
        leftColumn.add(createTimelineCard(), "wrap");

        rightColumn = new JPanel(new MigLayout("insets 0, gapy 20", "[fill, grow]", "[grow, fill][pref!]"));
        rightColumn.setOpaque(false);

        JPanel partsOperationsCard = createCardPanel();
        partsOperationsCard.setLayout(new MigLayout("insets 15, fill", "[grow]", "[][grow]"));
        partsOperationsCard.add(fault_process_info, "wrap, growx");
        partsOperationsCard.add(part_notes_info, "grow");

        JPanel paymentsCard = createCardPanel();
        paymentsCard.setLayout(new MigLayout("insets 15, fill", "[grow]", "[grow]"));
        paymentsCard.add(price_info, "grow");

        rightColumn.add(partsOperationsCard, "wrap, growy");
        rightColumn.add(paymentsCard, "growy");

        contentPanel.add(leftColumn, "grow");
        contentPanel.add(rightColumn, "grow");

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, "grow");
    }

    // --- KART METOTLARI VE YARDIMCILAR (Aynı Kalıyor) ---
    private JPanel createCustomerCard() {
        JPanel card = createCardPanel();
        card.setLayout(new MigLayout("insets 20, fillx, wrap 2", "[grow][]", "[]15[][][]"));
        JLabel title = new JLabel("Müşteri Bilgileri"); title.setIcon(new Ikon("icons/user.svg"));
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        lblCustomerName = new JLabel("-"); lblCustomerName.putClientProperty(FlatClientProperties.STYLE, "font: bold +3");
        lblCustomerPhone = new JLabel("-"); lblCustomerPhone.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        lblCustomerEmail = new JLabel("-"); lblCustomerEmail.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        card.add(title, "span 2"); card.add(lblCustomerName, "span 2"); card.add(lblCustomerPhone, "span 2"); card.add(lblCustomerEmail, "span 2");
        return card;
    }

    private JPanel createDeviceCard() {
        JPanel card = createCardPanel();
        card.setLayout(new MigLayout("insets 20, fillx", "[100!][grow]", "[]15[][][][]15[]5[]"));
        JLabel title = new JLabel("Cihaz Bilgileri"); title.setIcon(new Ikon("icons/tablet-smartphone.svg")); title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        lblDeviceType = new JLabel("-"); lblDeviceType.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        lblDeviceBrand = new JLabel("-"); lblDeviceBrand.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        lblDeviceModel = new JLabel("-"); lblDeviceModel.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        lblDeviceSerial = new JLabel("-"); lblDeviceSerial.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        card.add(title, "span 2, wrap");
        card.add(createMutedLabel("Tür:")); card.add(lblDeviceType, "wrap");
        card.add(createMutedLabel("Marka:")); card.add(lblDeviceBrand, "wrap");
        card.add(createMutedLabel("Model:")); card.add(lblDeviceModel, "wrap");
        card.add(createMutedLabel("Seri No:")); card.add(lblDeviceSerial, "wrap");
        card.add(createMutedLabel("Müşteri Şikayeti:"), "span 2, wrap");
        txtReportedFault = new JTextArea();
        txtReportedFault.setEditable(false);
        txtReportedFault.setLineWrap(true);
        txtReportedFault.setWrapStyleWord(true);
        txtReportedFault.putClientProperty(FlatClientProperties.STYLE, "background: lighten($Panel.background, 3%); border: 10,10,10,10;");
        card.add(txtReportedFault, "span 2, growx, h 60!");
        return card;
    }

    private JPanel createTimelineCard() {
        JPanel card = createCardPanel();
        card.setLayout(new MigLayout("insets 20, fillx", "[100!][grow, right]", "[]15[][]"));
        JLabel title = new JLabel("Zaman Çizelgesi"); title.setIcon(new Ikon("icons/clock.svg")); title.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        lblDateArrival = new JLabel("-"); lblDateArrival.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        lblDateEstimated = new JLabel("-"); lblDateEstimated.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        card.add(title, "span 2, wrap");
        card.add(createMutedLabel("Geliş:")); card.add(lblDateArrival, "wrap");
        card.add(createMutedLabel("Tahmini Bitiş:")); card.add(lblDateEstimated, "wrap");
        return card;
    }

    private JLabel createMutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        return label;
    }

    private JPanel createCardPanel() {
        JPanel panel = new JPanel();
        panel.putClientProperty(FlatClientProperties.STYLE, "background: lighten($Panel.background, 2%); arc: 15;");
        return panel;
    }



    @Override
    public void removeNotify() {
        super.removeNotify(); // Swing'in kendi temizlik işlemlerini yapmasına izin ver

        // Form ekrandan kaldırılıyor! (Başka menüye geçildi)
        // Eğer bekleyen bir otomatik kayıt varsa, hemen ZORLA kaydet.
        if (autoSaveTimer != null && autoSaveTimer.isRunning()) {
            autoSaveTimer.stop();
            forceSaveSync(); // Dikkat: Artık senkron (bekleten) kayıt yapmalıyız
        }
    }
}
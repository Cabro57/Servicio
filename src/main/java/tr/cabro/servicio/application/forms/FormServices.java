package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.i18n.phonenumbers.Phonenumber;
import net.miginfocom.swing.MigLayout;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import raven.modal.component.dashboard.CardBox;
import raven.modal.system.Form;
import raven.modal.system.FormManager;
import raven.modal.utils.SystemForm;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.editors.ActionButtonEditor;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.panels.edit.CustomerEditPanel;
import tr.cabro.servicio.application.panels.service.QuickIntakePanel;
import tr.cabro.servicio.application.renderer.*;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.util.Ikon;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.RepairService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;


@SystemForm(name = "Servis Kayıtları", description = "Tüm servis kayıtlarını oluşturmak için kullanılabilir")
public class FormServices extends Form {

    private final RepairService service;
    private TableRowSorter<GenericTableModel<Service>> sorter;

    public FormServices() {
        this.service = ServiceManager.getRepairService();

        init();
    }

    @Override
    public void formRefresh() {
        refreshTable();

        refreshStats();
    }

    @Override
    public void formOpen() {
        refreshTable();
    }

    private void init() {
        initComponent();

        setupTable();

        configureTableRenderers();

        initFilters();

        refreshTable();

        refreshStats();

        btnNew.addActionListener(e -> openQuickIntakeModal());

    }

    public void openQuickIntakeModal() {
        final String INTAKE_MODAL_ID = "quick_intake_modal";
        QuickIntakePanel intakePanel = new QuickIntakePanel(INTAKE_MODAL_ID, new Service());

        SimpleModalBorder.Option[] options = {
                new SimpleModalBorder.Option("Servisi Kaydet", SimpleModalBorder.OK_OPTION),
                new SimpleModalBorder.Option("Servisi Başlat", SimpleModalBorder.NO_OPTION),
                new SimpleModalBorder.Option("İptal", SimpleModalBorder.CANCEL_OPTION)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(intakePanel, "Servis Kayıdı", options, (controller, action) -> {

            if (action == SimpleModalBorder.OPENED) {
                intakePanel.formOpen();
            }else if (action == QuickIntakePanel.NEW_CUSTOMER_ACTION) {
                controller.consume(); // Ana modalı kapatma!

                CustomerEditPanel newCustomerPanel = new CustomerEditPanel(new Customer());
                ModalDialog.pushModal(new SimpleModalBorder(newCustomerPanel, "Yeni Müşteri", SimpleModalBorder.YES_NO_OPTION, (c1, a1) -> {
                    if (a1 == SimpleModalBorder.OPENED) {

                    } else if (a1 == SimpleModalBorder.YES_OPTION) {
                        Customer newCustomer = newCustomerPanel.getData();
                        if (newCustomer != null) {
                            c1.consume();
                            newCustomer.setCreatedAt(LocalDateTime.now());

                            ServiceManager.getCustomerService().save(newCustomer, false).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                                intakePanel.appendNewCustomer(saved); // Combobox'a ekle ve seç
                                ModalDialog.popModal(INTAKE_MODAL_ID); // Geri kay
                            }));
                        }
                    }
                }), INTAKE_MODAL_ID);
            }

            else if (action == SimpleModalBorder.NO_OPTION) {
                Service updated = intakePanel.getData();
                if (updated == null) {
                    controller.consume();
                    return;
                }

                service.save(updated, false).thenAccept(saved -> {
                    SwingUtilities.invokeLater(() -> {
                        Toast.show(this, Toast.Type.SUCCESS, "servis başarıyla kayıt edildi.");
                        refreshTable();
                        FormService form = new FormService(saved);
                        FormManager.showForm(form);
                    });
                }).exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        controller.consume();
                        Toast.show(this, Toast.Type.ERROR, "Hata: " + ex.getMessage());
                    });
                    Servicio.getLogger().error("Müşteri ekleme hatası", ex.getMessage());
                    return null;
                });
            } else if (action == SimpleModalBorder.OK_OPTION) {
                Service updated = intakePanel.getData();
                if (updated == null) {
                    controller.consume();
                    return;
                }

                service.save(updated, false).thenAccept(saved -> {
                    SwingUtilities.invokeLater(() -> {
                        Toast.show(this, Toast.Type.SUCCESS, "servis başarıyla kayıt edildi.");
                        refreshTable();
                    });
                }).exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        controller.consume();
                        Toast.show(this, Toast.Type.ERROR, "Hata: " + ex.getMessage());
                    });
                    Servicio.getLogger().error("Müşteri ekleme hatası", ex.getMessage());
                    return null;
                });
            }

        }), INTAKE_MODAL_ID);
    }

    private void setupTable() {

        List<ColumnDef<Service>> columns = Arrays.asList(
                new ColumnDef<>("Kayıt No", String.class, s -> "SRV-" + s.getId()), // ID formatlandı
                new ColumnDef<>("Müşteri Bilgisi", Customer.class, Service::getCustomer),
                new ColumnDef<>("Cihaz Bilgisi", Service.class, s -> s),
                new ColumnDef<>("Arıza / İşlem", String.class, Service::getDetectedFault), // Veya hangi değişkende tutuyorsan
                new ColumnDef<>("Tarih", Service.class, s -> s),
                new ColumnDef<>("Ücret", String.class, s -> Format.formatPrice(s.getRemainingAmount())),
                new ColumnDef<>("Durum", ServiceStatus.class, Service::getServiceStatus),
                new ColumnDef<>("İşlem", String.class, s -> "Detay")
        );

        serviceTableModel = new GenericTableModel<>(columns);
        table.setModel(serviceTableModel);
    }

    public void refreshTable() {
        if (serviceTableModel != null) {
            service.getAll().thenAccept(serviceList ->
                    SwingUtilities.invokeLater(() -> serviceTableModel.setData(serviceList))
            );
        }
    }

    private void refreshStats() {
        service.getDashboardStats().thenAccept(stats -> {
            SwingUtilities.invokeLater(() -> {
                // 0: Toplam Kayıt
                cardBox.setValueAt(0,
                        String.valueOf(stats.getTotalRecords()),
                        "Tüm zamanların toplam kaydı",
                        "",     // Değişim oranı (Şu an hesaplanmıyor)
                        true);  // Yön (true: yeşil/yukarı, false: kırmızı/aşağı)

                // 1: Aktif İşlemler
                cardBox.setValueAt(1,
                        String.valueOf(stats.getActiveRecords()),
                        "Şu an atölyede bekleyen cihazlar",
                        "",
                        true);

                // 2: Tamamlanan
                cardBox.setValueAt(2,
                        String.valueOf(stats.getCompletedRecords()),
                        "Teslim edilen veya hazır olanlar",
                        "",
                        true);

                // 3: Toplam Ciro
                cardBox.setValueAt(3,
                        Format.formatPrice(stats.getTotalRevenue()),
                        "Sistemdeki toplam gelir",
                        "",
                        true);
            });
        });
    }

    // Tablonun görselliği (Genişlik, Tarih formatları, Hizalamalar)
    private void configureTableRenderers() {
        // Tablo Genel Stilleri
        table.getTableHeader().putClientProperty(FlatClientProperties.STYLE,
                "height:40; hoverBackground:null; pressedBackground:null; separatorColor:$TableHeader.background; font:bold, +5;");

        table.putClientProperty(FlatClientProperties.STYLE,
                "rowHeight:52; showHorizontalLines:true; intercellSpacing:0,1; " +
                        "cellFocusColor:$TableHeader.hoverBackground; selectionBackground:$TableHeader.hoverBackground; " +
                        "selectionForeground:$Table.foreground; font: +2");

        // HİZALAMALAR (Yeni 7 Sütunlu yapıya uyarlandı)
        Integer[] columnAlignments = {
                SwingConstants.CENTER,  // 0: Kayıt No
                SwingConstants.LEADING, // 1: Müşteri
                SwingConstants.LEADING, // 2: Cihaz
                SwingConstants.LEADING, // 3: Arıza
                SwingConstants.LEADING, // 4: Tarih (Çift satır olacağı için sola veya merkeze yaslayabilirsin)
                SwingConstants.TRAILING,// 5: Ücret
                SwingConstants.CENTER,  // 6: Durum
                SwingConstants.CENTER   // 7: İşlem
        };
        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, columnAlignments));

        // 0. KAYIT NO VURGUSU (Bold ve Büyük)
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                label.setFont(label.getFont().deriveFont(Font.BOLD, 13f)); // Font büyütüldü ve kalınlaştırıldı
                label.setHorizontalAlignment(SwingConstants.CENTER);
                return label;
            }
        });

        // 1. MÜŞTERİ RENDERER
        table.getColumnModel().getColumn(1).setCellRenderer(
                new MultiLineTableCellRenderer<Customer>(
                        customer -> customer.getName() + " " + customer.getSurname(),
                        customer -> PhoneHelper.formatForDisplay(customer.getPhoneNumber1())
                )
        );

        // 2. CİHAZ RENDERER
        table.getColumnModel().getColumn(2).setCellRenderer(
                new MultiLineTableCellRenderer<Service>(
                        service -> service.getDeviceBrand() + " " + service.getDeviceModel(),
                        service -> "SN: " + (service.getDeviceSerial() != null ? service.getDeviceSerial() : "Bilinmiyor")
                )
        );

        // 4. TARİH RENDERER (Multi-Line Mantığı)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", new Locale("tr", "TR"));
        table.getColumnModel().getColumn(4).setCellRenderer(
                new MultiLineTableCellRenderer<Service>(
                        // ÜST METİN: Kayıt Tarihi
                        service -> service.getCreatedAt() != null ? service.getCreatedAt().format(formatter) : "Tarih Yok",

                        // ALT METİN: Teslim veya Tahmini
                        service -> {
                            if (service.getServiceStatus() == ServiceStatus.DELIVERED || service.getServiceStatus() == ServiceStatus.RETURN) {
                                return "Bitiş: " + (service.getDeliveryAt() != null ? service.getDeliveryAt().format(formatter) : "-");
                            } else {
                                // TODO: Eğer Service modelinde Tahmini Teslim Tarihi diye bir alan yoksa, buraya eklemelisin.
                                // Şimdilik kayıt tarihine 3 gün ekleyerek mock data oluşturuyoruz.
                                LocalDateTime estimated = service.getCreatedAt() != null ? service.getCreatedAt().plusDays(3) : null;
                                return "Tahmini: " + (estimated != null ? estimated.format(formatter) : "-");
                            }
                        }
                )
        );

        // 6. DURUM RENDERER
        table.getColumnModel().getColumn(6).setCellRenderer(new UniversalVisualizableRenderer());

        // 7. İŞLEM (BUTON) RENDERER
        table.getColumnModel().getColumn(7).setCellRenderer(new ActionButtonRenderer());
        table.getColumnModel().getColumn(7).setCellEditor(new ActionButtonEditor(new TableActionEvent() {
            @Override
            public void onEdit(int row) {
                if (table.isEditing()) {
                    table.getCellEditor().cancelCellEditing();
                }

                int modelRow = table.convertRowIndexToModel(row);
                Service selectedService = serviceTableModel.getItemAt(modelRow);

                if (selectedService != null) {
                    openEditModal(selectedService);
                }
            }

            @Override
            public void onDelete(int row) {
                if (table.isEditing()) {
                    table.getCellEditor().cancelCellEditing();
                }

                int modelRow = table.convertRowIndexToModel(row);
                Service selectedService = serviceTableModel.getItemAt(modelRow);

                if (selectedService != null) {
                    int confirm = JOptionPane.showConfirmDialog(
                            FormServices.this,
                            "SRV-" + selectedService.getId() + " numaralı servis kaydını silmek istediğinize emin misiniz?\nBu işlem geri alınamaz.",
                            "Silme Onayı",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                    );

                    if (confirm == JOptionPane.YES_OPTION) {
                        service.delete(selectedService.getId()).thenAccept(v -> {
                            // Başarılı olduğunda arayüzü (EDT) güncelle
                            SwingUtilities.invokeLater(() -> {
                                Toast.show(FormServices.this, Toast.Type.SUCCESS, "Kayıt başarıyla silindi.");
                                refreshTable(); // Tabloyu yenile
                                refreshStats(); // Üstteki istatistik kutularını yenile
                            });
                        }).exceptionally(ex -> {
                            // Hata durumunda kullanıcıya mesaj göster
                            SwingUtilities.invokeLater(() -> {
                                Toast.show(FormServices.this, Toast.Type.ERROR, "Silme işlemi başarısız: " + ex.getCause().getMessage());
                            });
                            return null;
                        });
                    }
                }
            }

            @Override
            public void onView(int row) {
                if (table.isEditing()) {
                    table.getCellEditor().cancelCellEditing();
                }

                int modelRow = table.convertRowIndexToModel(row);
                Service selectedService = serviceTableModel.getItemAt(modelRow);

                if (selectedService != null) {
                    FormManager.showForm(new FormService(selectedService));
                }
            }
        }));

        // GENİŞLİKLER (Yeni 7 Sütuna göre ayarlandı)
        table.getColumnModel().getColumn(0).setMaxWidth(100);
        table.getColumnModel().getColumn(0).setPreferredWidth(90);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);
        table.getColumnModel().getColumn(3).setPreferredWidth(200);
        table.getColumnModel().getColumn(4).setPreferredWidth(120);
        table.getColumnModel().getColumn(5).setPreferredWidth(100);
        table.getColumnModel().getColumn(6).setPreferredWidth(120);
    }

    /**
     * Müşteri ve Cihaz bilgilerini düzenlemek için Hızlı Kabul panelini Edit Modunda açar.
     */
    private void openEditModal(Service service) {
        if (service == null || service.getId() <= 0) return;

        final String EDIT_MODAL_ID = "service_edit_modal";

        // Ağır işlemleri (Panelin çizilmesi ve verilerin yüklenmesini) arka planda başlatıyoruz
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {

            // 1. Paneli oluşturma işlemi ana arayüzü (EDT) kilitlenmeden arka planda yapılır
            QuickIntakePanel editPanel = new QuickIntakePanel(EDIT_MODAL_ID, service);

            SimpleModalBorder.Option[] options = {
                    new SimpleModalBorder.Option("Değişiklikleri Kaydet", SimpleModalBorder.YES_OPTION),
                    new SimpleModalBorder.Option("İptal", SimpleModalBorder.CANCEL_OPTION)
            };

            // 2. Modalı saracak çerçeveyi arka planda hazırla
            return new SimpleModalBorder(editPanel, "Kayıt Düzenle (SRV-" + service.getId() + ")", options, (controller, action) -> {

                if (action == SimpleModalBorder.OPENED) {
                    editPanel.formOpen();
                }
                else if (action == QuickIntakePanel.NEW_CUSTOMER_ACTION) {
                    controller.consume();
                    CustomerEditPanel newCustomerPanel = new CustomerEditPanel(new Customer());
                    ModalDialog.pushModal(new SimpleModalBorder(newCustomerPanel, "Yeni Müşteri Ekle", SimpleModalBorder.YES_NO_OPTION, (c1, a1) -> {
                        if (a1 == SimpleModalBorder.YES_OPTION) {
                            Customer newCustomer = newCustomerPanel.getData();
                            if (newCustomer != null) {
                                c1.consume();
                                newCustomer.setCreatedAt(java.time.LocalDateTime.now());
                                ServiceManager.getCustomerService().save(newCustomer, false).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                                    editPanel.appendNewCustomer(saved);
                                    ModalDialog.popModal(EDIT_MODAL_ID);
                                }));
                            }
                        }
                    }), EDIT_MODAL_ID);
                }
                else if (action == SimpleModalBorder.YES_OPTION) {
                    Service updatedData = editPanel.getData();
                    if (updatedData == null) {
                        controller.consume();
                        return;
                    }
                    RepairService repairService = ServiceManager.getRepairService();
                    repairService.save(updatedData, true).thenAccept(saved -> {
                        SwingUtilities.invokeLater(() -> {
                            Toast.show(this, Toast.Type.SUCCESS, "Servis bilgileri güncellendi.");
                        });
                    }).exceptionally(ex -> {
                        SwingUtilities.invokeLater(() -> {
                            controller.consume();
                            Toast.show(this, Toast.Type.ERROR, "Hata: " + ex.getCause().getMessage());
                        });
                        return null;
                    });
                }
            });

        }).thenAccept(modalBorder -> {

            // 3. Her şey arka planda hazırlandıktan sonra, modalı ekranda
            // göstermek için güvenli bir şekilde ana arayüze (SwingUtilities) geri dön
            SwingUtilities.invokeLater(() -> {
                ModalDialog.showModal(this, modalBorder, EDIT_MODAL_ID);
            });

        });
    }

    private void initFilters() {
        sorter = new TableRowSorter<>(serviceTableModel);

        // --- MİMARİ DÜZELTME: Sorter'a karmaşık nesneleri nasıl sıralayacağını öğretiyoruz ---

        // 1. Kolon (Müşteri nesnesi taşıyor): İsme göre sırala
        sorter.setComparator(1, Comparator.comparing(c -> {
            Customer cust = (Customer) c;
            return cust.getName() + " " + cust.getSurname();
        }));

        // 2. Kolon (Cihaz için Service nesnesi taşıyor): Marka ve Modele göre sırala
        sorter.setComparator(2, Comparator.comparing(s -> {
            Service srv = (Service) s;
            return srv.getDeviceBrand() + " " + srv.getDeviceModel();
        }));

        // 4. Kolon (Tarih için Service nesnesi taşıyor): Oluşturulma Tarihine göre sırala
        sorter.setComparator(4, Comparator.comparing(s -> {
            LocalDateTime date = ((Service) s).getCreatedAt();
            // NullPointerException yememek için eğer tarih yoksa en eski tarihi veriyoruz
            return date != null ? date : LocalDateTime.MIN;
        }));

        table.setRowSorter(sorter);

        // Artık 4. Kolon (Tarih) sıralanabilir durumda olduğu için uygulama çökmeyecek
        List<RowSorter.SortKey> sortKeys = new ArrayList<>();
        sortKeys.add(new RowSorter.SortKey(4, SortOrder.DESCENDING));
        sorter.setSortKeys(sortKeys);

        // Arama kutusu dinleyicisi
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
        });
    }

    private void applyFilters() {
        List<RowFilter<TableModel, Object>> filters = new ArrayList<>();
        String searchText = searchField.getText();

        // DÜZELTME: Durum kolonu artık 6. indekste.
        if (currentStatusFilter != null) {
            filters.add(new RowFilter<TableModel, Object>() {
                @Override
                public boolean include(Entry<? extends TableModel, ? extends Object> entry) {
                    Object cellValue = entry.getValue(6);
                    return cellValue == currentStatusFilter;
                }
            });
        }

        if (searchText != null && !searchText.trim().isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(searchText)));
        }

        if (filters.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.andFilter(filters));
        }
    }

    private void initComponent() {
        setLayout(new MigLayout("fill, insets 15, gap 10, wrap", "[grow]", "[pref][pref][grow, fill]"));

        JPanel headerPanel = new JPanel(new MigLayout("insets 0, fillx", "[grow][]", "[][]"));
        headerPanel.setOpaque(false);

        JLabel title = new JLabel("Servis Kayıtları");
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +14");

        JLabel subtitle = new JLabel("Müşteri cihazlarının teknik servis durumlarını yönetin.");
        subtitle.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");

        btnNew = new JButton("Yeni Kayıt Oluştur");
        // Butonu FlatLaf'ın birincil (Primary) rengine boya ve ikon ekle
        btnNew.putClientProperty(FlatClientProperties.STYLE, "background: $Component.accentColor; foreground: #ffffff; arc: 10; margin: 5,10,5,10; iconTextGap: 23;");
        btnNew.setIcon(new Ikon("icons/plus.svg", btnNew.getFont().getSize())); // İkon yolunu kendine göre ayarla

        headerPanel.add(title, "cell 0 0");
        headerPanel.add(btnNew, "cell 1 0 1 2, aligny center"); // Sağda ortala
        headerPanel.add(subtitle, "cell 0 1");

        add(headerPanel, "wrap, growx");

        JPanel statsPanel = new JPanel(new MigLayout("insets 0, gapx 15, fillx", "[fill]", "[fill]"));
        statsPanel.setOpaque(false);

        cardBox = new CardBox();
        cardBox.addCardItem(new Ikon("icons/sigma.svg", 0.7f), "Toplam Kayıt");
        cardBox.addCardItem(new Ikon("icons/activity.svg", 0.7f), "Aktif İşlemler");
        cardBox.addCardItem(new Ikon("icons/check-check.svg", 0.7f), "Tamamlanan");
        cardBox.addCardItem(new Ikon("icons/badge-turkish-lira.svg", 0.7f), "Toplam Ciro");
        statsPanel.add(cardBox);

        add(statsPanel, "wrap, growx");

        JPanel tableContainer = new JPanel(new MigLayout("fill, insets 15, gapy 15", "[grow]", "[pref][grow]"));
        tableContainer.putClientProperty(FlatClientProperties.STYLE, "arc: 16; background: lighten($Panel.background, 3%);");

        JLabel tableTitle = new JLabel("Tüm Kayıtlar");
        tableTitle.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");

        searchField = new JTextField(20);
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Müşteri, cihaz veya ID ara...");
        searchField.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,10,4,10");
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new Ikon("icons/search.svg"));


        DefaultComboBoxModel<Object> comboModel = new DefaultComboBoxModel<>();
        comboModel.addElement("Tümü"); // null referansı yerine görsel bir String
        for (ServiceStatus status : ServiceStatus.values()) {
            comboModel.addElement(status);
        }
        filterCombo = new JComboBox<>(comboModel);
        filterCombo.putClientProperty(FlatClientProperties.STYLE, "arc: 10");

        filterCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ServiceStatus) {
                    setText(((ServiceStatus) value).getDisplayName());
                }
                return this;
            }
        });

        filterCombo.addActionListener(e -> {
            Object selected = filterCombo.getSelectedItem();
            if (selected instanceof ServiceStatus) {
                currentStatusFilter = (ServiceStatus) selected; // Doğrudan nesneyi sakla
            } else {
                currentStatusFilter = null; // "Tümü" seçildi
            }
            applyFilters();
        });

        JPanel toolbar = new JPanel(new MigLayout("insets 0, gapx 10", "[][grow][][]"));
        toolbar.setOpaque(false);
        toolbar.add(tableTitle);
        toolbar.add(searchField, "cell 2 0");
        toolbar.add(filterCombo, "cell 3 0");

        table = new JTable();
        table.setRowHeight(50); // İki satırlık veriler için satır yüksekliğini artırdık
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder()); // Çirkin kenarlığı sil

        tableContainer.add(toolbar, "wrap, growx, pushx");
        tableContainer.add(scrollPane, "grow, push");

        add(tableContainer, "grow");
    }

    private JTextField searchField;
    private JTable table;
    private JButton btnNew;
    private JComboBox<Object> filterCombo;
    private CardBox cardBox;

    private GenericTableModel<Service> serviceTableModel;

    private ServiceStatus currentStatusFilter = null;

}

package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import raven.modal.system.FormManager;
import raven.modal.utils.SystemForm;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.editors.ActionButtonEditor;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.forms.base.AbstractTableForm;
import tr.cabro.servicio.application.panels.edit.CustomerEditPanel;
import tr.cabro.servicio.application.panels.service.QuickIntakePanel;
import tr.cabro.servicio.application.renderer.*;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.util.Ikon;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
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
public class FormServices extends AbstractTableForm {

    private final RepairService service;
    private GenericTableModel<Service> tableModal;

    public FormServices() {
        this.service = ServiceManager.getRepairService();
    }

    @Override
    protected String getNewButtonText() {
        return "Yeni Kayıt Oluştur";
    }

    @Override
    protected String getNewButtonIconPath() {
        // Yeni ikon yolunu kendine göre ayarla (Örn: user-plus.svg)
        return "icons/plus.svg";
    }

    @Override
    protected String getTableTitleText() {
        return "Servis Kayıtları";
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Müşteri, cihaz veya ID ara...";
    }


    @Override
    protected boolean hasFilterCombo() {
        return true;
    }

    private ServiceStatus currentStatusFilter;

    @Override
    protected JComboBox<Object> createFilterCombo() {
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
            applyFilter();
        });

        return filterCombo;
    }

    @Override
    protected RowFilter<TableModel, Object> getCustomFilter() {
        RowFilter<TableModel, Object> filter = null;

        // DÜZELTME: Durum kolonu artık 6. indekste.
        if (currentStatusFilter != null) {
            filter = new RowFilter<TableModel, Object>() {
                @Override
                public boolean include(Entry<? extends TableModel, ? extends Object> entry) {
                    Object cellValue = entry.getValue(6);
                    return cellValue == currentStatusFilter;
                }
            };
        }

        return filter;
    }

    @Override
    protected void initCards() {
        cardBox.addCardItem(new Ikon("icons/sigma.svg", 0.7f), "Toplam Kayıt");
        cardBox.addCardItem(new Ikon("icons/activity.svg", 0.7f), "Aktif İşlemler");
        cardBox.addCardItem(new Ikon("icons/check-check.svg", 0.7f), "Tamamlanan");
        cardBox.addCardItem(new Ikon("icons/badge-turkish-lira.svg", 0.7f), "Toplam Ciro");
    }

    @Override
    protected void refreshStats() {
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
        }).exceptionally(ex -> {
            Servicio.getLogger().error("Müşteri istatistikleri yüklenirken hata oluştu", ex);
            return null;
        });
    }

    @Override
    protected void setupTable() {

        List<ColumnDef<Service>> columns = Arrays.asList(
                new ColumnDef<>("Kayıt No", String.class, s -> "SRV-" + s.getId()), // ID formatlandı
                new ColumnDef<>("Müşteri Bilgisi", Customer.class, Service::getCustomer),
                new ColumnDef<>("Cihaz Bilgisi", Device.class, Device::new),
                new ColumnDef<>("Arıza / İşlem", String.class, Service::getDetectedFault), // Veya hangi değişkende tutuyorsan
                new ColumnDef<>("Tarih", Service.class, s -> s),
                new ColumnDef<>("Ücret", String.class, s -> Format.formatPrice(s.getRemainingAmount())),
                new ColumnDef<>("Durum", ServiceStatus.class, Service::getServiceStatus),
                new ColumnDef<>("İşlem", String.class, s -> "Detay")
        );

        tableModal = new GenericTableModel<>(columns);
        setTableModel(tableModal);

        configureTableColumns();
    }

    @Override
    protected void initTableFilter(TableModel model) {
        sorter = new TableRowSorter<>(tableModal);

        // --- MİMARİ DÜZELTME: Sorter'a karmaşık nesneleri nasıl sıralayacağını öğretiyoruz ---

        // 1. Kolon (Müşteri nesnesi taşıyor): İsme göre sırala
        sorter.setComparator(1, Comparator.comparing(c -> {
            Customer cust = (Customer) c;
            return cust.getName() + " " + cust.getSurname() + " " + cust.getPhoneNumber1();
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

        List<RowSorter.SortKey> sortKeys = new ArrayList<>();
        sortKeys.add(new RowSorter.SortKey(4, SortOrder.DESCENDING));
        sorter.setSortKeys(sortKeys);
    }

    private void configureTableColumns() {

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
                label.putClientProperty(FlatClientProperties.STYLE, "font: $h3.font");
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
                new MultiLineTableCellRenderer<Device>(
                        device -> device.getBrand() + " " + device.getModel(),
                        device -> "SN: " + (device.getSerial() != null ? device.getSerial() : "Bilinmiyor")
                )
        );

        // 4. TARİH RENDERER (Dinamik Renklendirme ile)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", new Locale("tr", "TR"));
        table.getColumnModel().getColumn(4).setCellRenderer(
                new MultiLineTableCellRenderer<Service>(
                        // 1. Parametre: ÜST METİN (Kayıt Tarihi)
                        service -> service.getCreatedAt() != null ? service.getCreatedAt().format(formatter) : "Tarih Yok",

                        // 2. Parametre: ALT METİN (Bitiş / Tahmini)
                        service -> {
                            if (service.getServiceStatus() == ServiceStatus.DELIVERED || service.getServiceStatus() == ServiceStatus.RETURN) {
                                return "Bitiş: " + (service.getDeliveryAt() != null ? service.getDeliveryAt().format(formatter) : "-");
                            } else {
                                LocalDateTime estimated = service.getCreatedAt() != null ? service.getCreatedAt().plusDays(3) : null;
                                return "Tahmini: " + (estimated != null ? estimated.format(formatter) : "-");
                            }
                        },

                        // 3. Parametre: Üst Metin Rengi (null bırakıyoruz, varsayılanı kullansın)
                        service -> null,

                        // 4. Parametre: Alt Metin Rengi (Duruma göre yeşil veya varsayılan gri)
                        service -> {
                            if (service.getServiceStatus() == ServiceStatus.DELIVERED || service.getServiceStatus() == ServiceStatus.RETURN) {
                                // Resimdeki gibi tatlı, neon bir yeşil tonu (RGB)
                                return new Color(46, 204, 113);
                            }
                            // Teslim edilmemişse null dönerek FlatLaf'ın varsayılan gri rengini kullanmasını sağla
                            return null;
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
                Service selectedService = tableModal.getItemAt(modelRow);

                if (selectedService != null) {
                    openEditModal(selectedService);
                    btnNew.addActionListener(e -> onNew());
                }
            }

            @Override
            public void onDelete(int row) {
                if (table.isEditing()) {
                    table.getCellEditor().cancelCellEditing();
                }

                int modelRow = table.convertRowIndexToModel(row);
                Service selectedService = tableModal.getItemAt(modelRow);

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
                Service selectedService = tableModal.getItemAt(modelRow);

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

    @Override
    protected void refreshTable() {
        if (tableModal != null) {
            service.getAll().thenAccept(serviceList ->
                    SwingUtilities.invokeLater(() -> tableModal.setData(serviceList))
            ).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    Toast.show(this, Toast.Type.ERROR, "Veriler yüklenemedi: " + ex.getMessage());
                    Servicio.getLogger().error("Tablo yenileme hatası", ex);
                });
                return null;
            });
        }
    }

    @Override
    protected void onNew() {
        final String INTAKE_MODAL_ID = "quick_intake_modal";
        QuickIntakePanel intakePanel = new QuickIntakePanel(new Service());

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

    private void openEditModal(Service service) {
        if (service == null || service.getId() <= 0) return;

        final String EDIT_MODAL_ID = "service_edit_modal";

        // Ağır işlemleri (Panelin çizilmesi ve verilerin yüklenmesini) arka planda başlatıyoruz
        CompletableFuture.supplyAsync(() -> {

            // 1. Paneli oluşturma işlemi ana arayüzü (EDT) kilitlenmeden arka planda yapılır
            QuickIntakePanel editPanel = new QuickIntakePanel(service);

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
                                newCustomer.setCreatedAt(LocalDateTime.now());
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

}

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
import tr.cabro.servicio.application.panels.QuickIntakePanel;
import tr.cabro.servicio.application.renderer.*;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.util.Ikon;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.WorkOrderService;
import tr.cabro.servicio.service.ReportManager;
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

@SystemForm(name = "Servis Kayıtları", description = "Tüm servis kayıtlarını oluşturmak için kullanılabilir")
public class FormWorkOrders extends AbstractTableForm {

    private final WorkOrderService service;
    private final ReportManager reportManager;
    private GenericTableModel<WorkOrder> tableModal;
    private ServiceStatus currentStatusFilter;

    public FormWorkOrders() {
        this.service = ServiceManager.getWorkOrderService();
        this.reportManager = ServiceManager.getReportManager(); // YENİ: İstatistikler için eklendi
    }

    @Override
    protected String getNewButtonText() {
        return "Yeni Kayıt Oluştur";
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

    @Override
    protected JComboBox<Object> createFilterCombo() {
        DefaultComboBoxModel<Object> comboModel = new DefaultComboBoxModel<>();
        comboModel.addElement("Tümü");
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
                currentStatusFilter = (ServiceStatus) selected;
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
        if (currentStatusFilter != null) {
            filter = new RowFilter<TableModel, Object>() {
                @Override
                public boolean include(Entry<? extends TableModel, ? extends Object> entry) {
                    Object cellValue = entry.getValue(6); // 6. Kolon = Durum
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
        // YENİ: İstatistikleri artık WorkOrderService'den değil, ReportManager'dan alıyoruz (Tüm Zamanlar)
        reportManager.getDashboardSummaryCards("2000-01-01", "2100-01-01").thenAccept(stats -> {
            SwingUtilities.invokeLater(() -> {

                int completed = stats.getTotalRecords() - stats.getActiveRecords();

                cardBox.setValueAt(0,
                        String.valueOf(stats.getTotalRecords()),
                        "Tüm zamanların toplam kaydı",
                        "", true);

                cardBox.setValueAt(1,
                        String.valueOf(stats.getActiveRecords()),
                        "Şu an atölyede bekleyen cihazlar",
                        "", true);

                cardBox.setValueAt(2,
                        String.valueOf(completed),
                        "Teslim edilen veya iptal edilenler",
                        "", true);

                cardBox.setValueAt(3,
                        Format.formatPrice(stats.getTotalRevenue()),
                        "Sistemdeki brüt toplam ciro",
                        "", true);
            });
        }).exceptionally(ex -> {
            Servicio.getLogger().error("Servis istatistikleri yüklenirken hata oluştu", ex);
            return null;
        });
    }

    @Override
    protected void setupTable() {
        List<ColumnDef<WorkOrder>> columns = Arrays.asList(
                new ColumnDef<>("Kayıt No", String.class, s -> "SRV-" + s.getId()),
                new ColumnDef<>("Müşteri Bilgisi", Customer.class, WorkOrder::getCustomer),
                new ColumnDef<>("Cihaz Bilgisi", Device.class, WorkOrder::getDevice),
                new ColumnDef<>("Arıza / İşlem", String.class, WorkOrder::getDetectedFault),
                new ColumnDef<>("Tarih", WorkOrder.class, s -> s),
                new ColumnDef<>("Kalan Ücret", String.class, s -> Format.formatPrice(s.getRemainingAmount())),
                new ColumnDef<>("Durum", ServiceStatus.class, WorkOrder::getServiceStatus),
                new ColumnDef<>("İşlem", String.class, s -> "Detay")
        );

        tableModal = new GenericTableModel<>(columns);
        setTableModel(tableModal);
        configureTableColumns();
    }

    @Override
    protected void initTableFilter(TableModel model) {
        sorter = new TableRowSorter<>(tableModal);

        sorter.setComparator(1, Comparator.comparing(c -> {
            if (c == null) return "";
            Customer cust = (Customer) c;
            return cust.getFullName() + " " + cust.getPhoneNumber1();
        }));

        sorter.setComparator(2, Comparator.comparing(s -> {
            if (s == null) return "";
            Device device = (Device) s;
            return device.getBrand() + " " + device.getModel();
        }));

        sorter.setComparator(4, Comparator.comparing(s -> {
            if (s == null) return LocalDateTime.MIN;
            LocalDateTime date = ((WorkOrder) s).getCreatedAt();
            return date != null ? date : LocalDateTime.MIN;
        }));

        List<RowSorter.SortKey> sortKeys = new ArrayList<>();
        sortKeys.add(new RowSorter.SortKey(4, SortOrder.DESCENDING));
        sorter.setSortKeys(sortKeys);
    }

    private void configureTableColumns() {
        Integer[] columnAlignments = {
                SwingConstants.CENTER,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.TRAILING,
                SwingConstants.CENTER,
                SwingConstants.CENTER
        };
        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, columnAlignments));

        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                label.putClientProperty(FlatClientProperties.STYLE, "font: $h3.font");
                label.setHorizontalAlignment(SwingConstants.CENTER);
                return label;
            }
        });

        table.getColumnModel().getColumn(1).setCellRenderer(
                new MultiLineTableCellRenderer<Customer>(
                        customer -> customer != null ? customer.getFullName() : "Bilinmeyen Müşteri",
                        customer -> customer != null ? PhoneHelper.formatForDisplay(customer.getPhoneNumber1()) : ""
                )
        );

        table.getColumnModel().getColumn(2).setCellRenderer(
                new MultiLineTableCellRenderer<Device>(
                        device -> device != null ? device.getBrand() + " " + device.getModel() : "Bilinmeyen Cihaz",
                        device -> "SN: " + (device != null && device.getSerialNo() != null ? device.getSerialNo() : "Bilinmiyor")
                )
        );

        table.getColumnModel().getColumn(3).setCellRenderer(new TooltipCellRenderer());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", new Locale("tr", "TR"));
        table.getColumnModel().getColumn(4).setCellRenderer(
                new MultiLineTableCellRenderer<WorkOrder>(
                        service -> service.getCreatedAt() != null ? service.getCreatedAt().format(formatter) : "Tarih Yok",
                        service -> {
                            if (service.getServiceStatus() == ServiceStatus.DELIVERED || service.getServiceStatus() == ServiceStatus.RETURN) {
                                return "Bitiş: " + (service.getDeliveryDate() != null ? service.getDeliveryDate().format(formatter) : "-");
                            } else {
                                LocalDateTime estimated = service.getCreatedAt() != null ? service.getCreatedAt().plusDays(3) : null;
                                return "Tahmini: " + (estimated != null ? estimated.format(formatter) : "-");
                            }
                        },
                        service -> null,
                        service -> {
                            if (service.getServiceStatus() == ServiceStatus.DELIVERED || service.getServiceStatus() == ServiceStatus.RETURN) {
                                return new Color(46, 204, 113);
                            }
                            return null;
                        }
                )
        );

        table.getColumnModel().getColumn(6).setCellRenderer(new UniversalVisualizableRenderer());

        table.getColumnModel().getColumn(7).setCellRenderer(new ActionButtonRenderer());
        table.getColumnModel().getColumn(7).setCellEditor(new ActionButtonEditor(new TableActionEvent() {
            @Override
            public void onEdit(int row) {
                if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                int modelRow = table.convertRowIndexToModel(row);
                WorkOrder selectedWorkOrder = tableModal.getItemAt(modelRow);
                if (selectedWorkOrder != null) openEditModal(selectedWorkOrder);
            }

            @Override
            public void onDelete(int row) {
                if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                int modelRow = table.convertRowIndexToModel(row);
                WorkOrder selectedWorkOrder = tableModal.getItemAt(modelRow);

                if (selectedWorkOrder != null) {
                    int confirm = JOptionPane.showConfirmDialog(
                            FormWorkOrders.this,
                            "SRV-" + selectedWorkOrder.getId() + " numaralı servis kaydını silmek istediğinize emin misiniz?\nBu işlem geri alınamaz.",
                            "Silme Onayı",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                    );

                    if (confirm == JOptionPane.YES_OPTION) {
                        service.delete(selectedWorkOrder.getId()).thenAccept(v -> {
                            SwingUtilities.invokeLater(() -> {
                                Toast.show(FormWorkOrders.this, Toast.Type.SUCCESS, "Kayıt başarıyla silindi.");
                                refreshTable();
                            });
                        }).exceptionally(ex -> {
                            SwingUtilities.invokeLater(() -> Toast.show(FormWorkOrders.this, Toast.Type.ERROR, "Silme işlemi başarısız: " + ex.getCause().getMessage()));
                            return null;
                        });
                    }
                }
            }

            @Override
            public void onView(int row) {
                if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                int modelRow = table.convertRowIndexToModel(row);
                WorkOrder selectedWorkOrder = tableModal.getItemAt(modelRow);

                if (selectedWorkOrder==null) {
                    Toast.show(FormWorkOrders.this, Toast.Type.WARNING, "İstenen servis bulunamadı");
                    return;
                }

                service.get(selectedWorkOrder.getId()).thenAccept(workOrder -> {
                    SwingUtilities.invokeLater(() -> {
                        if (workOrder.isPresent()) {
                            FormManager.showForm(new FormWorkOrder(workOrder.get()));
                        } else {
                            Toast.show(FormWorkOrders.this, Toast.Type.WARNING, "Böyle bir servis bulunamadı.");
                        }
                    });
                }).exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        Toast.show(FormWorkOrders.this, Toast.Type.ERROR, ex.getMessage());
                    });
                    Servicio.getLogger().error("HATA: ", ex);
                    return null;
                });
            }
        }));

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
                    SwingUtilities.invokeLater(() -> {
                        tableModal.setData(serviceList);
                        refreshStats(); // Tablo yenilendikten sonra üst istatistikleri de tazele
                    })
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
        QuickIntakePanel intakePanel = new QuickIntakePanel(new WorkOrder());

        SimpleModalBorder.Option[] options = {
                new SimpleModalBorder.Option("Servisi Kaydet", SimpleModalBorder.OK_OPTION),
                new SimpleModalBorder.Option("Servisi Başlat", SimpleModalBorder.NO_OPTION),
                new SimpleModalBorder.Option("İptal", SimpleModalBorder.CANCEL_OPTION)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(intakePanel, "Servis Kayıdı", options, (controller, action) -> {

            if (action == SimpleModalBorder.OPENED) {
                intakePanel.formOpen();
            } else if (action == QuickIntakePanel.NEW_CUSTOMER_ACTION) {
                controller.consume(); // Ana modalı kapatma!

                CustomerEditPanel newCustomerPanel = new CustomerEditPanel(new Customer());
                ModalDialog.pushModal(new SimpleModalBorder(newCustomerPanel, "Yeni Müşteri", SimpleModalBorder.YES_NO_OPTION, (c1, a1) -> {
                    if (a1 == SimpleModalBorder.YES_OPTION) {
                        Customer newCustomer = newCustomerPanel.getData();
                        if (newCustomer != null) {
                            c1.consume();
                            newCustomer.setCreatedAt(LocalDateTime.now());
                            ServiceManager.getCustomerService().save(newCustomer, false).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                                intakePanel.appendNewCustomer(saved);
                                ModalDialog.popModal(INTAKE_MODAL_ID);
                            }));
                        }
                    }
                }), INTAKE_MODAL_ID);
            } else if (action == SimpleModalBorder.NO_OPTION) {
                WorkOrder updated = intakePanel.getData();
                if (updated == null) { controller.consume(); return; }

                service.save(updated, false).thenAccept(saved -> {
                    SwingUtilities.invokeLater(() -> {
                        Toast.show(this, Toast.Type.SUCCESS, "Servis başarıyla kayıt edildi.");
                        refreshTable();
                        FormManager.showForm(new FormWorkOrder(saved));
                    });
                }).exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        controller.consume();
                        Toast.show(this, Toast.Type.ERROR, "Hata: " + ex.getMessage());
                    });
                    return null;
                });
            } else if (action == SimpleModalBorder.OK_OPTION) {
                WorkOrder updated = intakePanel.getData();
                if (updated == null) { controller.consume(); return; }

                service.save(updated, false).thenAccept(saved -> {
                    SwingUtilities.invokeLater(() -> {
                        Toast.show(this, Toast.Type.SUCCESS, "Servis başarıyla kayıt edildi.");
                        refreshTable();
                    });
                }).exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        controller.consume();
                        Toast.show(this, Toast.Type.ERROR, "Hata: " + ex.getMessage());
                    });
                    return null;
                });
            }
        }), INTAKE_MODAL_ID);
    }

    private void openEditModal(WorkOrder editWorkOrder) {
        if (editWorkOrder == null || editWorkOrder.getId() <= 0) return;

        final String EDIT_MODAL_ID = "service_edit_modal";

        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            QuickIntakePanel editPanel = new QuickIntakePanel(editWorkOrder);

            SimpleModalBorder.Option[] options = {
                    new SimpleModalBorder.Option("Değişiklikleri Kaydet", SimpleModalBorder.YES_OPTION),
                    new SimpleModalBorder.Option("İptal", SimpleModalBorder.CANCEL_OPTION)
            };

            return new SimpleModalBorder(editPanel, "Kayıt Düzenle (SRV-" + editWorkOrder.getId() + ")", options, (controller, action) -> {
                if (action == SimpleModalBorder.OPENED) {
                    editPanel.formOpen();
                } else if (action == QuickIntakePanel.NEW_CUSTOMER_ACTION) {
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
                } else if (action == SimpleModalBorder.YES_OPTION) {
                    WorkOrder updatedData = editPanel.getData();
                    if (updatedData == null) {
                        controller.consume();
                        return;
                    }
                    service.save(updatedData, true).thenAccept(saved -> {
                        SwingUtilities.invokeLater(() -> {
                            Toast.show(this, Toast.Type.SUCCESS, "Servis bilgileri güncellendi.");
                            refreshTable();
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
        }).thenAccept(modalBorder -> SwingUtilities.invokeLater(() -> ModalDialog.showModal(this, modalBorder, EDIT_MODAL_ID)));
    }
}
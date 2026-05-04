package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import raven.modal.ModalDialog;
import tr.cabro.servicio.application.renderer.*;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import raven.modal.system.Form;
import raven.modal.system.FormManager;
import raven.modal.utils.SystemForm;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.editors.ActionButtonEditor;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.forms.base.AbstractTableForm;
import tr.cabro.servicio.application.panels.edit.CustomerEditPanel;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.util.Ikon;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.enums.CustomerType;
import tr.cabro.servicio.service.CustomerService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@SystemForm(name = "Müşteriler", description = "Müşteri veritabanını ve iletişim bilgilerini yönetin.")
public class FormCustomers extends AbstractTableForm {

    private final CustomerService customerService;
    private GenericTableModel<Customer> tableModel;

    public FormCustomers() {
        this.customerService = ServiceManager.getCustomerService();
    }

    // --- 1. ÜST KISIM VE ARAMA AYARLARI ---

    @Override
    protected String getNewButtonText() {
        return "Yeni Müşteri Ekle";
    }

    @Override
    protected String getNewButtonIconPath() {
        return "icons/user-plus.svg";
    }

    @Override
    protected String getTableTitleText() {
        return "Tüm Müşteriler";
    }

    @Override
    protected String getSearchPlaceholder() {
        return "İsim, telefon veya e-posta ara...";
    }

    // --- 2. İSTATİSTİK KARTLARI (DASHBOARD) ---

    @Override
    protected void initCards() {
        cardBox.addCardItem(new Ikon("icons/users.svg", 0.7f), "Toplam Müşteri");
        cardBox.addCardItem(new Ikon("icons/user.svg", 0.7f), "Normal Müşteri");
        cardBox.addCardItem(new Ikon("icons/store.svg", 0.7f), "Esnaf & Bayi");
        cardBox.addCardItem(new Ikon("icons/badge-turkish-lira.svg", 0.7f), "Toplam Ciro");
    }

    // Artık bu metodu tek başına çağırmıyoruz, refreshTable içinde her şeyi senkron yapıyoruz
    @Override
    protected void refreshStats() {
        customerService.getAllTable().thenAccept(customers -> {

            long totalCustomer = customers.size();

            long totalNormal = customers.stream()
                    .filter(c -> c.getType() == CustomerType.NORMAL)
                    .count();

            long totalBusiness = customers.stream()
                    .filter(c -> c.getType() == CustomerType.DEALER
                    || c.getType() == CustomerType.SMALL_BUSINESS)
                    .count();

            long totalCiro = customers.stream()
                    .map(Customer::getSpent)
                    .mapToLong(BigDecimal::longValue)
                    .sum();

            SwingUtilities.invokeLater(() -> {
                cardBox.setValueAt(0, String.valueOf(totalCustomer), " ", "", true);
                cardBox.setValueAt(1, String.valueOf(totalNormal), " ", "", true);
                cardBox.setValueAt(2, String.valueOf(totalBusiness), " ", "", true);
                cardBox.setValueAt(3, String.valueOf(totalCiro), " ", "", true);
            });
        }).exceptionally(throwable -> {

            SwingUtilities.invokeLater(() -> {
                Toast.show(this, Toast.Type.ERROR, throwable.getMessage());
            });

            Servicio.getLogger().error(throwable.getMessage(), throwable);
            return null;
        });
    }

    // --- 3. TABLO YAPILANDIRMASI ---

    @Override
    protected void setupTable() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", new Locale("tr", "TR"));

        List<ColumnDef<Customer>> columns = Arrays.asList(
                new ColumnDef<>("ID", String.class, c -> String.format("C-%03d", c.getId())),
                new ColumnDef<>("Müşteri Adı", Customer.class, c -> c),
                new ColumnDef<>("İletişim", Customer.class, c -> c),
                new ColumnDef<>("Cihaz Sayısı", Integer.class, Customer::getDeviceCount),
                new ColumnDef<>("Toplam Harcama", BigDecimal.class, Customer::getSpent),
                new ColumnDef<>("Kayıt Tarihi", String.class, c -> c.getCreatedAt() != null ? c.getCreatedAt().format(formatter) : "-"),
                new ColumnDef<>("İşlem", String.class, c -> "Detay")
        );

        tableModel = new GenericTableModel<>(columns);
        setTableModel(tableModel);

        configureTableColumns();
    }

    @Override
    protected void refreshTable() {
        customerService.getAllTable().thenAccept(customers -> {
            if (customers.isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    Toast.show(this, Toast.Type.INFO, "Müşteri Listesi Boş!");
                });
            }

            SwingUtilities.invokeLater(() -> {
                tableModel.setData(customers);
                refreshStats();
            });
        }).exceptionally(throwable -> {
            SwingUtilities.invokeLater(() -> {
                Toast.show(this, Toast.Type.ERROR, throwable.getMessage());
            });
            Servicio.getLogger().error(throwable.getMessage(), throwable);

            return null;
        });
    }

    private void configureTableColumns() {
        Integer[] columnAlignments = {
                SwingConstants.LEADING,  // ID
                SwingConstants.LEADING,  // Müşteri Adı
                SwingConstants.LEADING,  // İletişim
                SwingConstants.CENTER,   // Cihaz Sayısı
                SwingConstants.TRAILING, // Toplam Harcama
                SwingConstants.LEADING,  // Kayıt Tarihi
                SwingConstants.CENTER    // İşlem
        };
        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, columnAlignments));

        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                ((JLabel) c).setHorizontalAlignment(SwingConstants.LEADING);
                ((JLabel) c).putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: +1");
                return c;
            }
        });

        table.getColumnModel().getColumn(1).setCellRenderer(new CustomerTableCellRenderer());

        table.getColumnModel().getColumn(2).setCellRenderer(
                new MultiLineTableCellRenderer<Customer>(
                        c -> PhoneHelper.formatForDisplay(c.getPhoneNumber1()),
                        Customer::getEmail
                )
        );

        table.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                ((JLabel) c).setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        });

        table.getColumnModel().getColumn(4).setCellRenderer(new CurrencyTableCellRenderer());

        table.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                label.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
                label.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));
                return label;
            }
        });

        table.getColumnModel().getColumn(6).setCellRenderer(new ActionButtonRenderer());
        table.getColumnModel().getColumn(6).setCellEditor(new ActionButtonEditor(new TableActionEvent() {
            @Override
            public void onView(int row) {
                int modelRow = table.convertRowIndexToModel(row);
                Customer c = tableModel.getItemAt(modelRow);
                customerService.get(c.getId()).thenAccept(response -> {
                    response.ifPresent(customer -> SwingUtilities.invokeLater(() -> {
                        Form formInstance = new FormCustomer(customer);
                        Toast.show(FormCustomers.this, Toast.Type.INFO, customer.getFirstName() + " detaylarına bakılıyor...");
                        FormManager.showForm(formInstance);
                    }));
                }).exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> Toast.show(FormCustomers.this, Toast.Type.ERROR, ex.getMessage()));
                    return null;
                });
            }

            @Override
            public void onEdit(int row) {
                if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                int modelRow = table.convertRowIndexToModel(row);
                openEditModal(tableModel.getItemAt(modelRow));
            }

            @Override
            public void onDelete(int row) {
                if (table.isEditing()) table.getCellEditor().cancelCellEditing();
                int modelRow = table.convertRowIndexToModel(row);
                Customer selectedCustomer = tableModel.getItemAt(modelRow);

                int confirm = JOptionPane.showConfirmDialog(
                        FormCustomers.this,
                        selectedCustomer.getFullName() + " adlı müşteriyi silmek istediğinize emin misiniz?",
                        "Müşteri Sil",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );

                if (confirm == JOptionPane.YES_OPTION) {
                    customerService.delete(selectedCustomer.getId()).thenAccept(v -> {
                        SwingUtilities.invokeLater(() -> {
                            Toast.show(FormCustomers.this, Toast.Type.SUCCESS, "Müşteri silindi.");
                            refreshTable();
                        });
                    });
                }
            }
        }));

        table.getColumnModel().getColumn(0).setMaxWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(4).setPreferredWidth(130);
        table.getColumnModel().getColumn(5).setPreferredWidth(150);
        table.getColumnModel().getColumn(6).setMaxWidth(180);
        table.getColumnModel().getColumn(6).setMinWidth(120);
    }

    // DÜZELTME: Verileri ve istatistikleri aynı anda çeken asenkron yapı
//    @Override
//    protected void refreshTable() {
//        CompletableFuture<List<CustomersTableDto>> customersFuture = customerService.getAll();
//        CompletableFuture<List<WorkOrder>> servicesFuture = workOrderService.getAll();
//
//        CompletableFuture.allOf(customersFuture, servicesFuture).thenAccept(v -> {
//            List<CustomersTableDto> allCustomers = customersFuture.join();
//            List<WorkOrder> allWorkOrders = servicesFuture.join();
//
//            customerSpentMap.clear();
//            BigDecimal totalGlobalRevenue = BigDecimal.ZERO;
//
//            // Müşterilerin servis ödemelerini hesapla (Ciro)
//            for (WorkOrder s : allWorkOrders) {
//                if (s.getCustomerId() == null || s.getCustomerId() <= 0) continue;
//
//                BigDecimal servicePaid = BigDecimal.ZERO;
//                if (s.getPayments() != null) {
//                    for (WorkOrderPayment payment : s.getPayments()) {
//                        servicePaid = servicePaid.add(payment.getAmount());
//                    }
//                }
//
//                customerSpentMap.merge(s.getCustomerId(), servicePaid, BigDecimal::add);
//                totalGlobalRevenue = totalGlobalRevenue.add(servicePaid);
//            }
//
//            // Kart İstatistiklerini Hesapla
//            long totalCount = allCustomers.size();
//            long normalCount = allCustomers.stream()
//                    .filter(c -> c.getType() != null && c.getType() == CustomerType.NORMAL)
//                    .count();
//
//            long businessCount = allCustomers.stream()
//                    .filter(c -> c.getType() != null &&
//                            (c.getType() == CustomerType.SMALL_BUSINESS || c.getType() == CustomerType.DEALER))
//                    .count();
//
//            BigDecimal finalTotalRevenue = totalGlobalRevenue;
//
//            // Arayüzü (UI) Güvenli Şekilde Güncelle
//            SwingUtilities.invokeLater(() -> {
//                tableModel.setData(allCustomers);
//
//                cardBox.setValueAt(0, String.valueOf(totalCount), "Sistemdeki tüm kayıtlar", "", true);
//                cardBox.setValueAt(1, String.valueOf(normalCount), "Bireysel kullanıcılar", "", true);
//                cardBox.setValueAt(2, String.valueOf(businessCount), "İşletme ve ticari hesaplar", "", true);
//                cardBox.setValueAt(3, Format.formatPrice(finalTotalRevenue), "Tüm zamanların cirosu", "", true);
//            });
//
//        }).exceptionally(ex -> {
//            SwingUtilities.invokeLater(() -> {
//                Toast.show(this, Toast.Type.ERROR, "Veriler yüklenemedi: " + ex.getMessage());
//                Servicio.getLogger().error("Tablo yenileme hatası", ex);
//            });
//            return null;
//        });
//    }

    // --- 4. MODAL / PENCERE İŞLEMLERİ ---

    @Override
    protected void onNew() {
        final String id = "CustomerNew";
        CustomerEditPanel panel = new CustomerEditPanel(new Customer());

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Kaydet", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(panel, "Yeni Müşteri Ekle", options, (controller, action) -> {
            if (action == SimpleModalBorder.OK_OPTION) {
                Customer updated = panel.getData();
                if (updated == null) {
                    controller.consume();
                    return;
                }

                customerService.save(updated, false).thenAccept(saved -> {
                    SwingUtilities.invokeLater(() -> {
                        Toast.show(this, Toast.Type.SUCCESS, updated.getFullName() + " başarıyla eklendi.");
                        refreshTable();
                    });
                }).exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        Toast.show(this, Toast.Type.ERROR, "Hata: " + ex.getMessage());
                        controller.consume();
                    });
                    Servicio.getLogger().error(ex.getMessage(), ex);
                    return null;
                });
            }
        }), id);
    }

    private void openEditModal(Customer customer) {
        final String id = "CustomerEdit";
        CustomerEditPanel panel = new CustomerEditPanel(customer);

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Güncelle", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(panel, "Müşteri Düzenle", options, (controller, action) -> {
            if (action == SimpleModalBorder.OK_OPTION) {
                Customer updated = panel.getData();
                if (updated == null) {
                    controller.consume();
                    return;
                }

                updated.setId(customer.getId());
                updated.setCreatedAt(customer.getCreatedAt());

                customerService.save(updated, true).thenAccept(saved -> {
                    SwingUtilities.invokeLater(() -> {
                        Toast.show(this, Toast.Type.SUCCESS, updated.getFullName() + " başarıyla güncellendi.");
                        refreshTable();
                    });
                }).exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        controller.consume();
                        Toast.show(this, Toast.Type.ERROR, "Güncelleme Hatası: " + ex.getMessage());
                    });
                    return null;
                });
            }
        }), id);
    }
}
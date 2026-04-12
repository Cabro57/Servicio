package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import raven.modal.ModalDialog;
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
import tr.cabro.servicio.application.renderer.ActionButtonRenderer;
import tr.cabro.servicio.application.renderer.CustomerTableCellRenderer;
import tr.cabro.servicio.application.renderer.MultiLineTableCellRenderer;
import tr.cabro.servicio.application.renderer.TableHeaderAlignment;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.util.Ikon;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.enums.CustomerType;
import tr.cabro.servicio.service.CustomerService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
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
        // Yeni ikon yolunu kendine göre ayarla (Örn: user-plus.svg)
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

    @Override
    protected void refreshStats() {
        // Servis üzerinden asenkron olarak tüm müşterileri çekiyoruz
        customerService.getAll().thenAccept(allCustomers -> {

            // 1. Toplam Müşteri
            long totalCount = allCustomers.size();

            // 2. Normal Müşteriler
            long normalCount = allCustomers.stream()
                    .filter(c -> c.getType() == CustomerType.NORMAL)
                    .count();

            // 3. Ticari Müşteriler (Esnaf ve Bayi olanları Kurumsal gibi topluyoruz)
            long businessCount = allCustomers.stream()
                    .filter(c -> c.getType() == CustomerType.SMALL_BUSINESS || c.getType() == CustomerType.DEALER)
                    .count();

            // 4. Toplam Ciro (Veritabanında şu an ciro kolonu yok.
            // İleride "Servisler" tablosu ile bu müşterilere ait ödenmiş servisleri toplayarak SQL'den çekebilirsin.)
            double totalRevenue = 0.0;

            // UI güncellemelerini mutlaka Swing EDT thread'inde yapıyoruz
            SwingUtilities.invokeLater(() -> {
                cardBox.setValueAt(0, String.valueOf(totalCount), "Sistemdeki tüm kayıtlar", "", true);
                cardBox.setValueAt(1, String.valueOf(normalCount), "Bireysel kullanıcılar", "", true);
                cardBox.setValueAt(2, String.valueOf(businessCount), "İşletme ve ticari hesaplar", "", true);
                cardBox.setValueAt(3, Format.formatPrice(totalRevenue), "Bakım aşamasında", "", true);
            });

        }).exceptionally(ex -> {
            Servicio.getLogger().error("Müşteri istatistikleri yüklenirken hata oluştu", ex);
            return null;
        });
    }

    // --- 3. TABLO YAPILANDIRMASI ---

    @Override
    protected void setupTable() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", new Locale("tr", "TR"));

        List<ColumnDef<Customer>> columns = Arrays.asList(
                new ColumnDef<>("ID", String.class, c -> String.format("C-%03d", c.getId())),
                new ColumnDef<>("Müşteri Adı", Customer.class, c -> c), // Özel renderer için nesneyi paslıyoruz
                new ColumnDef<>("İletişim", Customer.class, c -> c),    // MultiLine için nesneyi paslıyoruz
                new ColumnDef<>("Cihaz Sayısı", Integer.class, Customer::getDeviceCount), // Örnek alan
                new ColumnDef<>("Toplam Harcama", String.class, c -> Format.formatPrice(500)), // Örnek alan
                new ColumnDef<>("Kayıt Tarihi", String.class, c -> c.getCreatedAt() != null ? c.getCreatedAt().format(formatter) : "-"),
                new ColumnDef<>("İşlem", String.class, c -> "Detay")
        );

        tableModel = new GenericTableModel<>(columns);
        setTableModel(tableModel);

        configureTableColumns();
    }

    private void configureTableColumns() {
        // Hizalamalar (Görseldeki gibi: ID merkez, Sayılar merkez/sağ, diğerleri sol)
        Integer[] columnAlignments = {
                SwingConstants.LEADING,   // ID
                SwingConstants.LEADING,  // Müşteri Adı
                SwingConstants.LEADING,  // İletişim
                SwingConstants.CENTER,   // Cihaz Sayısı
                SwingConstants.TRAILING, // Toplam Harcama
                SwingConstants.LEADING,  // Kayıt Tarihi
                SwingConstants.CENTER    // İşlem
        };
        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, columnAlignments));

        // 0. Kolon: ID (Koyu renk font)
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                ((JLabel) c).setHorizontalAlignment(SwingConstants.LEADING);
                ((JLabel) c).putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: +1");
                return c;
            }
        });

        // 1. Kolon: Müşteri Adı TODO: buraya badge eklenecek müşteri tipi belirtilecek
        table.getColumnModel().getColumn(1).setCellRenderer(new CustomerTableCellRenderer());

        // 2. Kolon: İletişim (Telefon üstte, e-posta altta)
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

        table.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                label.setHorizontalAlignment(SwingConstants.TRAILING);
                label.setFont(label.getFont().deriveFont(Font.BOLD));
                label.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));
                return label;
            }
        });

        table.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                label.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
                label.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 15));
                return label;
            }
        });


        // 6. Kolon: İşlem Butonları
        table.getColumnModel().getColumn(6).setCellRenderer(new ActionButtonRenderer());
        table.getColumnModel().getColumn(6).setCellEditor(new ActionButtonEditor(new TableActionEvent() {
            @Override
            public void onView(int row) {
                // Müşteri Detay Ekranını açma (İleride eklenebilir)
                int modelRow = table.convertRowIndexToModel(row);
                Customer c = tableModel.getItemAt(modelRow);
                customerService.get(c.getId()).thenAccept(response -> {
                    response.ifPresent(customer -> SwingUtilities.invokeLater(() -> {
                        Form formInstance = new FormCustomer(customer);
                        Toast.show(FormCustomers.this, Toast.Type.INFO, customer.getName() + " detaylarına bakılıyor...");
                        FormManager.showForm(formInstance);
                    }));
                }).exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        Toast.show(FormCustomers.this, Toast.Type.ERROR, ex.getMessage());
                    });
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
                        selectedCustomer.getName() + " adlı müşteriyi silmek istediğinize emin misiniz?",
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

        // Genişlik Ayarları
        table.getColumnModel().getColumn(0).setMaxWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(4).setPreferredWidth(130);
        table.getColumnModel().getColumn(5).setPreferredWidth(150);
        table.getColumnModel().getColumn(6).setMaxWidth(180);
        table.getColumnModel().getColumn(6).setMinWidth(120);
    }

    @Override
    protected void refreshTable() {
        customerService.getAll().thenAccept(allCustomers -> {
            SwingUtilities.invokeLater(() -> {
                tableModel.setData(allCustomers);
                refreshStats(); // Tablo yenilendikten sonra üst kartları da güncelle
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                Toast.show(this, Toast.Type.ERROR, "Veriler yüklenemedi: " + ex.getMessage());
                Servicio.getLogger().error("Tablo yenileme hatası", ex);
            });
            return null;
        });
    }

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
                        Toast.show(this, Toast.Type.SUCCESS, updated.getName() + " başarıyla eklendi.");
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
                        Toast.show(this, Toast.Type.SUCCESS, updated.getName() + " başarıyla güncellendi.");
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
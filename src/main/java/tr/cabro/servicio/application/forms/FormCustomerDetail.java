package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import raven.modal.component.chart.themes.DefaultChartTheme;
import raven.modal.component.dashboard.CardBox;
import raven.modal.simple.SimpleMessageModal;
import raven.modal.system.Form;
import raven.modal.system.FormManager;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.panels.edit.CustomerEditPanel;
import tr.cabro.servicio.application.renderer.*;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.util.Ikon;
import tr.cabro.servicio.component.ProfileCard;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.service.CustomerService;
import tr.cabro.servicio.service.RepairService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public class FormCustomerDetail extends Form {

    private Customer customer;
    private final RepairService repairService;
    private final CustomerService customerService;

    private JTable table;
    private CardBox cardBox;
    private ProfileCard profileCard;
    private JLabel titleLabel;

    private GenericTableModel<Service> tableModel;

    public FormCustomerDetail(Customer customer) {
        this.customer = customer;
        this.repairService = ServiceManager.getRepairService();
        this.customerService = ServiceManager.getCustomerService();

        init();
    }

    private void init() {
        setLayout(new MigLayout("fill, wrap, insets 10, gap 10", "[300!, fill][grow, fill]", "[][grow]"));

        createHeader();
        createProfileCard();
        createRightPanel();

        setupTable();

        configureTable();

        refreshData();
    }

    @Override
    public void formRefresh() {
        // Form tekrar görüntülendiğinde verileri tazele
        // Müşteriyi veritabanından tekrar çek (değişiklik olmuş olabilir)
        customerService.get(customer.getId()).thenAccept(updated -> {
            updated.ifPresent(customer -> {
                this.customer = customer;
                refreshData();
            });
        });
    }

    private void setupTable() {
        List<ColumnDef<Service>> columns = Arrays.asList(
                new ColumnDef<>("#", Integer.class, Service::getId),
                new ColumnDef<>("Ürün", String.class, s -> s.getDeviceBrand() + " " + s.getDeviceModel()),
                new ColumnDef<>("Ücret", String.class, s -> Format.formatPrice(s.getRemainingAmount())),
                new ColumnDef<>("Durum", String.class, Service::getServiceStatus),
                new ColumnDef<>("Kayıt Tarihi", LocalDateTime.class, Service::getCreatedAt)
        );

        tableModel = new GenericTableModel<>(columns);
        table.setModel(tableModel);
    }

    public void refreshTable(List<Service> services) {
        if (tableModel != null) {
            tableModel.setData(services);
        }
    }

    private void refreshData() {
        // Profil kartını güncelle
        profileCard.setProfileData(customer);
        titleLabel.setText(customer.getName() + " " + customer.getSurname());

        // Servis verilerini çek
        repairService.getAll(customer.getId()).thenAccept(services -> {
            // İstatistikleri hesapla
            calculateAndSetStats(services);
            refreshTable(services);
        });

    }

    private void calculateAndSetStats(List<Service> services) {
        int totalServices = services.size();
        double totalRevenue = 0;
        double totalDebt = 0;

        for (Service s : services) {
            double laborCost = s.getLaborCost();
            double partsCost = s.getTotalPartsCost();
            double totalCost = laborCost + partsCost;

            totalRevenue += s.getPaid();
            totalDebt += (totalCost - s.getPaid());
        }

        // CardBox indeksleri oluşturulma sırasına göredir:
        // 0: Toplam Servis
        // 1: Toplam Ödeme (Gelir)
        // 2: Kalan Borç (Gider ikonuyla gösterdik ama borç anlamında)

        cardBox.setValueAt(0,
                String.valueOf(totalServices),
                "Adet Servis Kaydı",
                null, true);

        cardBox.setValueAt(1,
                String.format("₺%,.2f", totalRevenue),
                "Toplam Tahsilat",
                null, true);

        cardBox.setValueAt(2,
                String.format("₺%,.2f", totalDebt),
                "Kalan Bakiye / Borç",
                totalDebt > 0 ? "Borçlu" : "Temiz",
                totalDebt <= 0); // Borç yoksa yeşil, varsa kırmızı
    }

    private void createHeader() {
        JPanel header = new JPanel(new MigLayout("insets 0, fillx", "[grow][]"));

        titleLabel = new JLabel(customer.toString());
        titleLabel.putClientProperty(FlatClientProperties.STYLE, "font:bold +5");

        JPanel actions = new JPanel(new MigLayout("insets 0"));

        JButton btnEdit = new JButton("Düzenle", new Ikon("icons/user-pen.svg", 0.7f));
        JButton btnDelete = new JButton("Sil", new Ikon("icons/user-minus.svg", 0.7f));

        btnEdit.addActionListener(e -> onEdit());
        btnDelete.addActionListener(e -> onDelete());

        actions.add(btnEdit);
        actions.add(btnDelete);

        header.add(titleLabel);
        header.add(actions);

        add(header, "span 2, growx");
    }

    private void createProfileCard() {
        profileCard = new ProfileCard();
        add(profileCard, "cell 0 1, growy, aligny top");
    }

    private void createRightPanel() {
        JPanel rightPanel = new JPanel(new MigLayout("fill, wrap, insets 0", "[grow]", "[]10[grow]"));

        // 1. İstatistik Kartları
        cardBox = new CardBox();
        cardBox.addCardItem(createIcon("icons/dashboard/customer.svg", DefaultChartTheme.getColor(0)), "Toplam Servis");
        cardBox.addCardItem(createIcon("icons/dashboard/income.svg", DefaultChartTheme.getColor(1)), "Toplam Ödeme");
        cardBox.addCardItem(createIcon("icons/dashboard/expense.svg", DefaultChartTheme.getColor(2)), "Kalan Borç");

        rightPanel.add(cardBox, "growx");

        // 2. Tablo
        JPanel tablePanel = new JPanel(new MigLayout("fill, insets 0"));
        tablePanel.putClientProperty(FlatClientProperties.STYLE_CLASS, "dashboardBackground");

        table = new JTable();
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        tablePanel.add(scroll, "grow");
        rightPanel.add(tablePanel, "grow");

        add(rightPanel, "cell 1 1, grow");
    }

    private void configureTable() {
        // Tablo stilleri
        table.setRowHeight(35);
        table.getTableHeader().putClientProperty(FlatClientProperties.STYLE, "font:bold;");
        table.setShowHorizontalLines(true);

        // Renderer ayarları
        Integer[] alignments = {SwingConstants.CENTER, SwingConstants.LEADING, SwingConstants.TRAILING, SwingConstants.CENTER, SwingConstants.CENTER};
        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, alignments));

        table.getColumnModel().getColumn(2).setCellRenderer(new CurrencyTableCellRenderer());
        table.getColumnModel().getColumn(3).setCellRenderer(new UniversalVisualizableRenderer());
        table.getColumnModel().getColumn(4).setCellRenderer(new DateTimeTableCellRenderer());

        // Sütun Genişlikleri
        table.getColumnModel().getColumn(0).setMaxWidth(50); // ID
        table.getColumnModel().getColumn(2).setPreferredWidth(100); // Ücret
        table.getColumnModel().getColumn(2).setMaxWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(180); // Durum
        table.getColumnModel().getColumn(3).setMaxWidth(200);
        table.getColumnModel().getColumn(4).setPreferredWidth(100); // Tarih
        table.getColumnModel().getColumn(4).setMaxWidth(120);

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() != -1) {
                    int viewRow = table.getSelectedRow();
                    int modelRow = table.convertRowIndexToModel(viewRow);

                    Service service = tableModel.getItemAt(modelRow);
                    if (service != null) {
                        FormService form = new FormService(service);
                        FormManager.showForm(form);
                    }
                }
            }
        });
    }

    private void onEdit() {
        final String id = "CustomerDetailEdit";
        CustomerEditPanel panel = new CustomerEditPanel(customer);

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Güncelle", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(
                        panel, "Müşteri Düzenle", options,
                        (controller, action) -> {
                            if (action == SimpleModalBorder.OPENED) {
//                                panel.populateFormWith(customer);
                            } else if (action == SimpleModalBorder.OK_OPTION) {
                                Customer updated = panel.getData();
                                if (updated == null) {
                                    controller.consume();
                                    return;
                                }

                                updated.setId(customer.getId());
                                updated.setCreatedAt(customer.getCreatedAt());
                                customerService.save(updated, true).thenAccept(customer -> {
                                    this.customer = updated; // Local reference update
                                    Toast.show(this, Toast.Type.SUCCESS, "Müşteri güncellendi.");
                                    refreshData();
                                }).exceptionally(ex -> {
                                    controller.consume();
                                    Toast.show(this, Toast.Type.ERROR, "Hata: " + ex.getMessage());
                                    return null;
                                }); // Update
                            }
                        })
                , id);
    }

    private void onDelete() {
        ModalDialog.showModal(this, new SimpleMessageModal(SimpleMessageModal.Type.INFO,
                "Müşteriyi silmek istediğinizden emin misiniz?\nBu müşteriye ait tüm servis kayıtları da silinecektir!",
                "Silme Onayı",
                SimpleModalBorder.YES_NO_OPTION, (controller, action) -> {
            if (action == 0) { // YES
                customerService.delete(customer.getId()).thenAccept(Void -> {
                    Toast.show(FormManager.getFrame(), Toast.Type.SUCCESS, "Müşteri ve kayıtları silindi.");
                    FormManager.undo();

                }).exceptionally(ex -> {
                    Toast.show(this, Toast.Type.ERROR, "Silme hatası: " + ex.getMessage());
                    Servicio.getLogger().error("Customer delete error", ex);
                    return null;
                });
            }
        }));
    }

    private Icon createIcon(String icon, Color color) {
        return new FlatSVGIcon(icon, 0.4f).setColorFilter(new FlatSVGIcon.ColorFilter(color1 -> color));
    }
}
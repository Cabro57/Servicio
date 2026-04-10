package tr.cabro.servicio.application.panels.service;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.component.PanelAction;
import tr.cabro.servicio.application.editors.ActionButtonEditor;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.panels.PartSearchPanel;
import tr.cabro.servicio.application.panels.ServicePanel;
import tr.cabro.servicio.application.panels.edit.ServicePartEditPanel;
import tr.cabro.servicio.application.renderer.ActionButtonRenderer;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.util.Ikon;
import tr.cabro.servicio.model.AddedPart;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.service.RepairService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PartsNotesInfoPanel extends ServicePanel {

    private GenericTableModel<AddedPart> tableModel;
    private final RepairService repairService;

    public PartsNotesInfoPanel() {
        this.repairService = ServiceManager.getRepairService();
        init();
    }

    private void init() {
        initComponent();
        setupTable();

        part_add_button.addActionListener(e -> addPartsCmd());
        new_part_add_button.addActionListener(e -> newPartCmd());
    }

    @Override
    protected void onServiceSet() {
        if (service == null) return;

        notes_field.setText(service.getNotes() != null ? service.getNotes() : "");

        boolean isRegistered = service.getId() > 0;
        part_add_button.setEnabled(isRegistered);
        new_part_add_button.setEnabled(isRegistered);

        if (!isRegistered) {
            String tooltip = "Parça eklemek için önce servisi kaydetmelisiniz.";
            part_add_button.setToolTipText(tooltip);
            new_part_add_button.setToolTipText(tooltip);
        } else {
            part_add_button.setToolTipText(null);
            new_part_add_button.setToolTipText(null);
        }

        refreshTable();
        updateTotals();
    }

    private void setupTable() {
        List<ColumnDef<AddedPart>> columns = Arrays.asList(
                new ColumnDef<>("Parça", String.class, AddedPart::getName),
                new ColumnDef<>("Adet", String.class, p -> String.valueOf(p.getAmount())),
                new ColumnDef<>("Satış Fiyatı", String.class, p -> Format.formatPrice(p.getSellingPrice())),
                new ColumnDef<>("İşlem", String.class, p -> "")  // Aksiyon sütunu (3. İndeks)
        );

        // MİMARİ DÜZELTME: Tablo modelinin isCellEditable metodunu ezerek buton kolonunu tıklanabilir (aktif) yapıyoruz!
        tableModel = new GenericTableModel<AddedPart>(columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // Sadece 3. indeks (İşlem sütunu) editable olsun, diğerleri salt okunur kalsın.
                return column == 3;
            }
        };

        parts_table.setModel(tableModel);

        configureTable();
    }

    private void configureTable() {
        parts_table.getColumnModel().getColumn(3).setCellRenderer(new ActionButtonRenderer());
        parts_table.getColumnModel().getColumn(0).setMinWidth(90);
        parts_table.getColumnModel().getColumn(1).setPreferredWidth(60);
        parts_table.getColumnModel().getColumn(3).setMaxWidth(110);
        parts_table.getColumnModel().getColumn(3).setMinWidth(110);
        parts_table.setFocusable(false);

        parts_table.getColumnModel().getColumn(3).setCellEditor(new ActionButtonEditor(new TableActionEvent() {
            @Override
            public void onEdit(int row) {
                if (parts_table.isEditing()) parts_table.getCellEditor().stopCellEditing();
                int modelRow = parts_table.convertRowIndexToModel(row);
                AddedPart addedPart = tableModel.getItemAt(modelRow);

                if (addedPart != null) {
                    editPartCmd(addedPart);

                }
            }

            @Override
            public void onDelete(int row) {
                if (parts_table.isEditing()) parts_table.getCellEditor().stopCellEditing();
                if (row < 0 || row >= service.getAddedParts().size()) return;

                AddedPart partToDelete = service.getAddedParts().get(row);

                int choice = JOptionPane.showConfirmDialog(PartsNotesInfoPanel.this,
                        "Bu parçayı servisten çıkarmak istediğinize emin misiniz?\n(Stoklu ürün ise stoğa iade edilecek)",
                        "Parça Çıkar", JOptionPane.YES_NO_OPTION);

                if (choice == JOptionPane.YES_OPTION) {
                    partToDelete.setReturnToStockOnDelete(true);

                    repairService.removeServicePart(partToDelete).thenAccept(v -> {
                        SwingUtilities.invokeLater(() -> {
                            // DÜZELTME: Doğrudan listeyi yenile.
                            pullLatestParts();
                            Toast.show(PartsNotesInfoPanel.this, Toast.Type.SUCCESS, "Parça çıkarıldı.");
                            if (getListener() != null) getListener().onDataChanged();
                        });
                    }).exceptionally(ex -> {
                        SwingUtilities.invokeLater(() -> Toast.show(PartsNotesInfoPanel.this, Toast.Type.ERROR, "Silme hatası: " + ex.getCause().getMessage()));
                        return null;
                    });
                }
            }

            @Override
            public void onView(int row) {
                if (parts_table.isEditing()) parts_table.getCellEditor().stopCellEditing();
                if (row < 0 || row >= service.getAddedParts().size()) return;

                AddedPart partToView = service.getAddedParts().get(row);
                // Şimdilik basit bir mesaj kutusu ile detayı gösterelim
                String details = String.format("Parça Adı: %s\nAdet: %d\nAlış Fiyatı: %s\nSatış Fiyatı: %s\nStok Takibi: %s",
                        partToView.getName(),
                        partToView.getAmount(),
                        Format.formatPrice(partToView.getPurchasePrice()),
                        Format.formatPrice(partToView.getSellingPrice()),
                        partToView.isStockTracked() ? "Evet" : "Hayır");

                JOptionPane.showMessageDialog(PartsNotesInfoPanel.this, details, "Parça Detayı", JOptionPane.INFORMATION_MESSAGE);
            }
        }));
    }

    // YENİ METOT: DB'den güncel parça listesini çekip arayüzü (State) temizler.
    private void pullLatestParts() {
        if (service == null || service.getId() <= 0) return;
        repairService.getServiceParts(service.getId()).thenAccept(guncelParcalar -> {
            SwingUtilities.invokeLater(() -> {
                service.setAddedParts(guncelParcalar); // Local state'i DB ile senkronize et
                refreshTable();
                updateTotals();
            });
        });
    }

    private void refreshTable() {
        if (service != null && service.getAddedParts() != null) {
            tableModel.setData(new ArrayList<>(service.getAddedParts()));
        } else {
            tableModel.setData(new ArrayList<>());
        }
    }

    private void newPartCmd() {
        final String id = "PartNew";
        ServicePartEditPanel panel = new ServicePartEditPanel(new AddedPart());

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Tamam", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(panel, "Manuel Parça Ekle", options,
                (controller, action) -> {
                    if (action == SimpleModalBorder.OK_OPTION) {
                        AddedPart newPart = panel.getData();
                        if (newPart == null) {
                            controller.consume();
                            return;
                        }

                        newPart.setServiceId(service.getId());
                        newPart.setStockTracked(false);

                        repairService.addServicePart(newPart).thenAccept(savedPart -> {
                            SwingUtilities.invokeLater(() -> {
                                pullLatestParts(); // Sadece arayüze ekleme, DB'den taze çek
                                if (getListener() != null) getListener().onDataChanged();
                                Toast.show(this, Toast.Type.SUCCESS, "Manuel parça eklendi.");
                            });
                        }).exceptionally(ex -> {
                            SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, "Hata: " + ex.getCause().getMessage()));
                            return null;
                        });
                    }
                }), id);
    }

    // YENİ METOT: Parça Düzenleme Modalı
    private void editPartCmd(AddedPart partToEdit) {
        final String id = "PartEdit";
        ServicePartEditPanel panel = new ServicePartEditPanel(partToEdit);

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Kaydet", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(panel, "Parçayı Düzenle", options,
                (controller, action) -> {
                    if (action == SimpleModalBorder.OK_OPTION) {
                        AddedPart updatedPart = panel.getData();
                        if (updatedPart == null) {
                            controller.consume();
                            return;
                        }

                        // HATA ÇÖZÜMÜ: Artık addServicePart (Insert) değil, updateServicePart (Update) çağırıyoruz!
                        repairService.updateServicePart(updatedPart).thenAccept(savedPart -> {
                            SwingUtilities.invokeLater(() -> {
                                pullLatestParts();
                                if (getListener() != null) getListener().onDataChanged();
                                Toast.show(this, Toast.Type.SUCCESS, "Parça başarıyla güncellendi.");
                            });
                        }).exceptionally(ex -> {
                            SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, "Hata: " + ex.getCause().getMessage()));
                            return null;
                        });
                    }
                }), id);
    }

    private void addPartsCmd() {
        final String id = "PartAdd";
        PartSearchPanel panel = new PartSearchPanel();

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Tamam", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(panel, "Stoktan Parça Ekle", options,
                (controller, action) -> {
                    if (action == SimpleModalBorder.OK_OPTION) {
                        List<Part> selectedParts = panel.getSelected();
                        if (selectedParts == null || selectedParts.isEmpty()) return;

                        // Tüm ekleme işlemlerini (Future'ları) bir listede toplayacağız
                        List<CompletableFuture<AddedPart>> futures = new ArrayList<>();

                        for (Part p : selectedParts) {
                            AddedPart addedPart = new AddedPart(p);
                            addedPart.setServiceId(service.getId());
                            // Her bir parçanın eklenme işlemini listeye ekle
                            futures.add(repairService.addServicePart(addedPart));
                        }

                        Toast.show(this, Toast.Type.INFO, "Parçalar ekleniyor, lütfen bekleyin...");

                        // HATA ÇÖZÜMÜ: Tüm parçaların veritabanına eklenmesi bittikten sonra (allOf) listeyi tek seferde çek!
                        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                                .thenRun(() -> {
                                    SwingUtilities.invokeLater(() -> {
                                        pullLatestParts();
                                        if (getListener() != null) getListener().onDataChanged();
                                        Toast.show(this, Toast.Type.SUCCESS, selectedParts.size() + " parça başarıyla eklendi.");
                                    });
                                })
                                .exceptionally(ex -> {
                                    SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, "Bazı parçalar eklenirken hata oluştu!"));
                                    return null;
                                });
                    }
                }), id);
    }

    public String getNotes() {
        return notes_field.getText().trim();
    }

    public void setNotes(String notes) {
        notes_field.setText(notes);
    }

    private void updateTotals() {
        if (getListener() != null && service != null && service.getAddedParts() != null) {
            double total = service.getAddedParts().stream().mapToDouble(AddedPart::getTotal).sum();
            getListener().onPartChange(total);
        }
    }

    private void initComponent() {
        setLayout(new MigLayout("fill, insets 5", "[grow]", "[][grow][][pref!]"));

        putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");

        JLabel title = new JLabel("Parçalar ve Notlar");
        title.setFont(title.getFont().deriveFont(18f).deriveFont(java.awt.Font.BOLD));

        product_search_field = new JTextField();
        product_search_field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Eklenen ürünlerde ara...");
        product_search_field.putClientProperty(FlatClientProperties.STYLE_CLASS, "serviceSearchField");

        FlatSVGIcon searchIcon = new Ikon("icons/search.svg");
        product_search_field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, searchIcon);

        part_add_button = new JButton("Stoktan Ekle");
        new_part_add_button = new JButton("Manuel Ekle");

        parts_table = new JTable();
        parts_table.setRowHeight(30);

        JScrollPane table_scroll = new JScrollPane(parts_table);

        parts_table.getTableHeader().putClientProperty(FlatClientProperties.STYLE,
                "height:30; hoverBackground:null; pressedBackground:null; separatorColor:$TableHeader.background; font:bold;");
        parts_table.putClientProperty(FlatClientProperties.STYLE,
                "rowHeight:25; showHorizontalLines:true; intercellSpacing:0,1; selectionBackground:$TableHeader.hoverBackground; selectionForeground:$Table.foreground;");

        notes_field = new JTextArea(3, 0);
        notes_field.setLineWrap(true);
        notes_field.setWrapStyleWord(true);
        JScrollPane notes_scroll = new JScrollPane(notes_field);

        add(title, "span 3, align left, gapbottom 10, wrap");
        add(product_search_field, "growx, split 3");
        add(part_add_button, "gapleft 5");
        add(new_part_add_button, "gapleft 5, wrap");
        add(table_scroll, "grow, wrap");
        add(new JLabel("Notlar:"), "aligny top, split 2");
        add(notes_scroll, "growx, growy, wrap");
    }

    JTable parts_table;
    JButton new_part_add_button;
    JTextField product_search_field;
    JTextArea notes_field;
    JButton part_add_button;
}
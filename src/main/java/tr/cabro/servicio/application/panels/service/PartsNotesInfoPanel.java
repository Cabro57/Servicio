package tr.cabro.servicio.application.panels.service;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;
import raven.modal.ModalDialog;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.editors.*;
import tr.cabro.servicio.application.events.TableActionEvent;
import tr.cabro.servicio.application.panels.PartSearchPanel;
import tr.cabro.servicio.application.panels.ServicePanel;
import tr.cabro.servicio.application.panels.edit.ServicePartEditPanel;
import tr.cabro.servicio.application.renderer.ActionButtonRenderer;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.model.AddedPart;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.application.context.ServiceContext;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PartsNotesInfoPanel extends ServicePanel {

    private GenericTableModel<AddedPart> tableModel;
    private final List<AddedPart> addedPartsList = new ArrayList<>();

    public PartsNotesInfoPanel(ServiceContext context) {
        super(context);
        init();
    }

    private void init() {
        initComponent();

        // Buton eventleri
        part_add_button.addActionListener(e -> addPartsCmd());
        new_part_add_button.addActionListener(e -> newPartCmd());

        List<ColumnDef<AddedPart>> columns = Arrays.asList(
                new ColumnDef<>("Parça", String.class, AddedPart::getName),
                new ColumnDef<>("Adet", String.class, p -> String.valueOf(p.getAmount())),
                new ColumnDef<>("Satış Fiyatı", String.class, p -> Format.formatPrice(p.getSellingPrice())),
                new ColumnDef<>("", String.class, p -> "")  // Aksiyon sütunu placeholder
        );
        tableModel = new GenericTableModel<>(addedPartsList, columns);
        parts_table.setModel(tableModel);

        parts_table.setFocusable(false);

        parts_table.getColumnModel().getColumn(3).setCellEditor(new ActionButtonEditor(new TableActionEvent() {
            @Override
            public void onEdit(int row) {
                if (row < 0 || row >= addedPartsList.size()) return;
                AddedPart data = addedPartsList.get(row);
                if (data == null) return;

                final String id = "ServicePartEdit";
                ServicePartEditPanel panel = new ServicePartEditPanel(data);

                SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                        new SimpleModalBorder.Option("Tamam", 0),
                        new SimpleModalBorder.Option("İptal", 2)
                };

                ModalDialog.showModal(getParent(), new SimpleModalBorder(
                        panel, "Servis Parça Formu", options,
                        (controller, action) -> {
                            if (action == SimpleModalBorder.OPENED) {
                                //panel.populateFormWith(data);
                            } else if (action == SimpleModalBorder.OK_OPTION) {
                                AddedPart update = panel.getData();

                                if (update == null) {
                                    controller.consume();
                                    return;
                                }

                                update.setCreatedAt(data.getCreatedAt());

                                addedPartsList.set(row, update);
                                tableModel.fireTableRowsUpdated(row, row);
                                updateMaterialCost();
                            }
                        }
                ), id);
            }

            @Override
            public void onDelete(int row) {
                if (row >= 0 && row < addedPartsList.size()) {
                    addedPartsList.remove(row);
                    tableModel.fireTableRowsDeleted(row, row);
                    updateMaterialCost();
                }
            }

            @Override
            public void onView(int row) {

            }
        }));
        parts_table.getColumnModel().getColumn(3).setCellRenderer(new ActionButtonRenderer());

        parts_table.getColumnModel().getColumn(0).setMinWidth(90);
        parts_table.getColumnModel().getColumn(1).setPreferredWidth(60);
        parts_table.getColumnModel().getColumn(3).setMaxWidth(110);
        parts_table.getColumnModel().getColumn(3).setMinWidth(110);
    }

    private void newPartCmd() {
        final String id = "PartNew";
        ServicePartEditPanel panel = new ServicePartEditPanel(new AddedPart());

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Tamam", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(
                        panel, "Parça Formu", options,
                        (controller, action) -> {
                            if (action == SimpleModalBorder.OPENED) {
                                //panel.clearForm();

                            } else if (action == SimpleModalBorder.OK_OPTION) {
                                AddedPart updated = panel.getData();

                                if (updated == null) {
                                    controller.consume();
                                    return;
                                }

                                addedPartsList.add(updated);
                                tableModel.fireTableRowsInserted(addedPartsList.size() - 1, addedPartsList.size() - 1);
                                updateMaterialCost();
                            }
                        })
                , id);
    }

    private void addPartsCmd() {
        final String id = "PartAdd";
        PartSearchPanel panel = new PartSearchPanel();

        SimpleModalBorder.Option[] options = new SimpleModalBorder.Option[]{
                new SimpleModalBorder.Option("Tamam", 0),
                new SimpleModalBorder.Option("İptal", 2)
        };

        ModalDialog.showModal(this, new SimpleModalBorder(
            panel, "Parça Tablosu", options,
                (controller, action) -> {
                    if (action == SimpleModalBorder.OPENED) {
                        panel.getSelected().clear();

                    } else if (action == SimpleModalBorder.OK_OPTION) {
                        List<Part> parts = panel.getSelected();
                        if (parts != null && !parts.isEmpty()) {
                            for (Part part : parts) {
                                AddedPart addedPart = new AddedPart(part);
                                addedPartsList.add(addedPart);
                            }
                            tableModel.fireTableDataChanged();
                            updateMaterialCost();
                        }
                    }
                })
        , id);
    }

    private void updateMaterialCost() {
        double total = addedPartsList.stream()
                .mapToDouble(AddedPart::getTotal)
                .sum();
        listener.onPartChange(total);
    }

    public List<AddedPart> getAddedParts() {
        return new ArrayList<>(addedPartsList);
    }

    public void setAddedParts(List<AddedPart> parts) {
        addedPartsList.clear();
        if (parts != null) {
            addedPartsList.addAll(parts);
        }
        tableModel.fireTableDataChanged();
        updateMaterialCost();
    }

    public String getNotes() {
        return notes_field.getText().trim();
    }

    public void setNotes(String notes) {
        notes_field.setText(notes);
    }

    private void initComponent() {
        setLayout(new MigLayout("fill, insets 5", "[grow]", "[][grow][][pref!]"));

        putClientProperty(FlatClientProperties.STYLE_CLASS, "editServicePanel");

        JLabel title = new JLabel("Arıza ve İşlem Bilgileri");
        title.setFont(title.getFont().deriveFont(18f).deriveFont(java.awt.Font.BOLD));

        product_search_field = new JTextField();
        product_search_field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Eklenen ürünlerde ara...");
        product_search_field.putClientProperty(FlatClientProperties.STYLE_CLASS, "serviceSearchField");

        FlatSVGIcon searchIcon = new FlatSVGIcon("icons/search.svg", 0.4f);
        JButton searchButton = new JButton(searchIcon);
        searchButton.setMargin(new Insets(1, 1, 1, 2));
        product_search_field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, searchButton);

        part_add_button = new JButton("Parça Ekle");

        new_part_add_button = new JButton("Yeni Parça Ekle");

        parts_table = new JTable();
        parts_table.setRowHeight(30);

        JScrollPane table_scroll = new JScrollPane(parts_table);

        parts_table.getTableHeader().putClientProperty(FlatClientProperties.STYLE,
                "height:30; hoverBackground:null; pressedBackground:null; separatorColor:$TableHeader.background; font:bold;");
        parts_table.putClientProperty(FlatClientProperties.STYLE,
                "rowHeight:25; showHorizontalLines:true; intercellSpacing:0,1; selectionBackground:$TableHeader.hoverBackground; selectionForeground:$Table.foreground;");

        table_scroll.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE,
                "trackArc:999; trackInsets:3,3,3,3; thumbInsets:3,3,3,3; background:$Table.background;");


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

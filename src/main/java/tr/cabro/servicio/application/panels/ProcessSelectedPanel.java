package tr.cabro.servicio.application.panels;

import lombok.Setter;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.renderer.ProcessTableRenderer;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.model.Labor;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

@Deprecated
public class ProcessSelectedPanel extends JPanel {

    private final GenericTableModel<Labor> model;
    @Setter
    private Consumer<Labor> onProcessDoubleClick;

    public ProcessSelectedPanel() {
        List<ColumnDef<Labor>> columns = Arrays.asList(
                new ColumnDef<>("İşlem Adı", Process.class, p -> p),
                new ColumnDef<>("Fiyat", BigDecimal.class, Labor::getDefaultPrice)
        );
        model = new GenericTableModel<>(columns);

        init();
    }

    private void init() {
        initComponent();

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row != -1) {
                        int modelRow = table.convertRowIndexToModel(row);
                        Labor l = model.getItemAt(modelRow);
                        if (onProcessDoubleClick != null && l != null) {
                            onProcessDoubleClick.accept(l);
                        }
                    }
                }
            }
        });

    }

    public void setProcess(List<Labor> labors) {
        model.setData(labors);
    }

    public List<Labor> getSelectedLabors() {
        return model.getSelectedItems(table.getSelectedRows());
    }

    private void initComponent() {
        setLayout(new MigLayout("fill,wrap,insets 5 30 5 30, width 400", "[fill, grow]", "[]"));

        table = new JTable();
        table.setModel(model);

        table.getColumnModel().getColumn(0).setCellRenderer(new ProcessTableRenderer());

        table.setRowHeight(55);

        add(new JScrollPane(table), "grow");
    }


    private JTable table;
}

package tr.cabro.servicio.application.panels;

import lombok.Setter;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.renderer.ProcessTableRenderer;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.model.Process;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class ProcessSelectedPanel extends JPanel {

    private GenericTableModel<Process> model;
    @Setter
    private Consumer<Process> onProcessDoubleClick;

    public ProcessSelectedPanel() {
        List<ColumnDef<Process>> columns = Arrays.asList(
                new ColumnDef<>("İşlem Adı", Process.class, p -> p),
                new ColumnDef<>("Fiyat", String.class, p -> Format.formatPrice(p.getPrice()))
        );
        model = new GenericTableModel<>(new ArrayList<>(), columns);

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
                        Process p = model.getItemAt(modelRow);
                        if (onProcessDoubleClick != null && p != null) {
                            onProcessDoubleClick.accept(p);
                        }
                    }
                }
            }
        });

    }

    public void setProcess(List<Process> processes) {
        model.setData(processes);
    }

    public List<Process> getSelectedProcesses() {
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

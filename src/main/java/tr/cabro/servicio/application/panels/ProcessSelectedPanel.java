package tr.cabro.servicio.application.panels;

import lombok.Setter;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.renderer.CheckBoxTableHeaderRenderer;
import tr.cabro.servicio.application.renderer.ProcessTableRenderer;
import tr.cabro.servicio.application.tablemodal.ProcessTableModel;
import tr.cabro.servicio.model.Process;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ProcessSelectedPanel extends JPanel {

    ProcessTableModel model;
    @Setter
    private Consumer<Process> onProcessDoubleClick;

    public ProcessSelectedPanel() {
        model = new ProcessTableModel(new ArrayList<>());

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
                        Process p = model.getProcess(row);
                        // Burada direkt ekleme yapabilirsin
                        // Ama ServiceEditUI'ya ulaşmak için callback lazım
                        if (onProcessDoubleClick != null) {
                            onProcessDoubleClick.accept(p);
                        }
                    }
                }
            }
        });

    }

    public void setProcess(List<Process> processes) {
        model.setProcesses(processes);
    }

    public List<Process> getSelectedProcesses() {
        return model.getSelectedProcess();
    }

    private void initComponent() {
        setLayout(new MigLayout("fill,wrap,insets 5 30 5 30, width 400", "[fill, grow]", "[]"));

        table = new JTable();
        table.setModel(model);

        table.getColumnModel().getColumn(0).setHeaderRenderer(new CheckBoxTableHeaderRenderer(table, 0));
        table.getColumnModel().getColumn(1).setCellRenderer(new ProcessTableRenderer());

        table.getColumnModel().getColumn(0).setMaxWidth(50);

        table.setRowHeight(55);

        add(new JScrollPane(table), "grow");
    }


    private JTable table;
}

package tr.cabro.servicio.application.ui;

import lombok.Getter;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.model.Process;
import tr.cabro.servicio.settings.DeviceSettings;
import tr.cabro.servicio.application.tablemodal.ProcessTableModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class ProcessSelectedUI extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTable process;

    @Getter
    Process selectedProcess;

    public ProcessSelectedUI(String deviceType) {
        setContentPane(contentPane);
        setModal(true);
        setTitle(deviceType + " - İşlem Seç");

        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screen_size.width * 0.55);
        int height = (int) (screen_size.height * 0.60);
        setSize(width, height);
        setLocationRelativeTo(null);

        // İşlemleri ayarlardan çek ve liste hazırla
        DeviceSettings settings = Servicio.getDeviceSettings();
        List<Process> processes = settings.getProcesses(deviceType);

        // Tablo modeli
        ProcessTableModel tableModel = new ProcessTableModel(processes);
        process.setModel(tableModel);


        // OK butonu
        buttonOK.addActionListener(e -> onOK());

        // Cancel butonu
        buttonCancel.addActionListener(e -> onCancel());

        // Çift tıklama
        process.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    onOK();
                }
            }
        });

        // Kapatma olayları
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        contentPane.registerKeyboardAction(e -> onCancel(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        setLocationRelativeTo(null);
    }

    private void onOK() {
        int selectedRow = process.getSelectedRow();
        if (selectedRow != -1) {
            ProcessTableModel model = (ProcessTableModel) process.getModel();
            selectedProcess = model.getProcess(selectedRow);
        } else {
            selectedProcess = null;
        }
        dispose();
    }



    private void onCancel() {
        selectedProcess = null;
        dispose();
    }
}

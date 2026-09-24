package tr.cabro.servicio.application.panels.workorder;

import tr.cabro.servicio.application.utils.Toasts;
import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.WorkOrderService;

import javax.swing.*;

/**
 * İş emri gövdesinin ilk adımı, "1 Arıza ve tespit": solda müşteri şikâyeti ve teknisyenin
 * tespit alanı, sağda teknisyen notları ({@link WorkOrderNotesPanel}, kendi kartı olmadan).
 * Tespit kaydedilince adım işareti onaya döner ({@code onDiagnosisChanged}).
 */
public class WorkOrderDiagnosisPanel extends JPanel {

    private final WorkOrder workOrder;
    private final WorkOrderService workOrderService;
    private final Runnable onDiagnosisChanged;
    private final WorkOrderPanelSupport.StepMarker stepMarker =
            new WorkOrderPanelSupport.StepMarker(1, "Arıza tespiti yazıldı", "Teknisyen tespiti henüz yazılmadı");

    private JTextArea txtDetectedFault;

    public WorkOrderDiagnosisPanel(WorkOrder workOrder, Runnable onDiagnosisChanged) {
        this.workOrder = workOrder;
        this.workOrderService = ServiceManager.getWorkOrderService();
        this.onDiagnosisChanged = onDiagnosisChanged;
        build();
    }

    public WorkOrderPanelSupport.StepMarker getStepMarker() {
        return stepMarker;
    }

    private void build() {
        putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        setLayout(new MigLayout("insets 14 16 14 16, fillx, gapx 28", "[grow 55, fill][grow 45, fill]", "[]12[top, grow, fill]"));

        add(WorkOrderPanelSupport.createStepHeader(stepMarker, "Arıza ve tespit", null), "span 2, growx, wrap");

        // Sol yarı sağdan bağımsız akar: şikâyet, tespit kutusu (notlar uzadıkça uzar), kaydet düğmesi.
        // Sağ yarının alt düğmesiyle aynı çizgide biter, arada boş kalan alan yoktur.
        JPanel fault = new JPanel(new MigLayout("insets 0, fillx, wrap, hidemode 3", "[grow, fill]", "[]4[]14[]4[grow, fill]8[]"));
        fault.setOpaque(false);

        fault.add(WorkOrderPanelSupport.createCaption("Müşteri şikâyeti"));
        String reported = workOrder.getReportedFault();
        boolean hasReported = reported != null && !reported.isBlank();
        JTextArea txtReported = new JTextArea(hasReported ? reported : "Belirtilmemiş");
        txtReported.setEditable(false);
        txtReported.setFocusable(false);
        txtReported.setOpaque(false);
        txtReported.setLineWrap(true);
        txtReported.setWrapStyleWord(true);
        txtReported.putClientProperty(FlatClientProperties.STYLE, hasReported
                ? "font: bold; border: 0,0,0,0" : "border: 0,0,0,0; foreground: $Label.disabledForeground");
        fault.add(txtReported, "wmin 0");

        fault.add(WorkOrderPanelSupport.createCaption("Teknisyen tespiti"));
        txtDetectedFault = WorkOrderPanelSupport.createHintArea("Tespit edilen arızayı ve yapılacak işi yazın…");
        txtDetectedFault.setText(workOrder.getDetectedFault() != null ? workOrder.getDetectedFault() : "");
        txtDetectedFault.getAccessibleContext().setAccessibleName("Teknisyen tespiti");
        JScrollPane detectedScroll = new JScrollPane(txtDetectedFault);
        detectedScroll.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
        fault.add(detectedScroll, "hmin 84, wmin 0");

        JButton btnSave = new JButton("Tespiti kaydet");
        btnSave.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,10,4,10");
        btnSave.addActionListener(e -> saveDetectedFault());
        fault.add(btnSave, "growx 0, al right");

        add(fault, "wmin 0, hmin 0, growy");
        add(new WorkOrderNotesPanel(workOrder), "wmin 0, aligny top");
    }

    private void saveDetectedFault() {
        String text = txtDetectedFault.getText().trim();
        workOrderService.updateDetectedFault(workOrder.getId(), text).thenRun(() ->
                SwingUtilities.invokeLater(() -> {
                    workOrder.setDetectedFault(text);
                    if (onDiagnosisChanged != null) onDiagnosisChanged.run();
                    Toasts.show(this, Toast.Type.SUCCESS, Messages.get("toast.detectedFault.saved"));
                })
        ).exceptionally(ex -> ErrorHandler.handle(this, "Arıza tespiti kaydedilemedi", ex));
    }
}

package raven.modal.system;

import lombok.Getter;
import raven.modal.Drawer;
import raven.modal.ModalDialog;
import raven.modal.component.SimpleModalBorder;
import raven.modal.component.About;
import raven.modal.utils.UndoRedo;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.panels.PinPanel;
import tr.cabro.servicio.application.panels.SetupPanel;
import tr.cabro.servicio.application.forms.FormDashboard;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.service.UserService;

import javax.swing.*;

public class FormManager {

    protected static final UndoRedo<Form> FORMS = new UndoRedo<>();
    @Getter
    private static JFrame frame;
    private static MainForm mainForm;
    private static PinPanel pinPanel;

    public static void install(JFrame f) {
        frame = f;
        install();
        logout();
    }

    private static void install() {
        FormSearch.getInstance().installKeyMap(getMainForm());
    }

    public static void showForm(Form form) {
        if (form != FORMS.getCurrent()) {
            FORMS.add(form);
            form.formCheck();
            form.formOpen();
            mainForm.setForm(form);
            mainForm.refresh();
        }
    }

    public static void undo() {
        if (FORMS.isUndoAble()) {
            Form form = FORMS.undo();
            form.formCheck();
            form.formOpen();
            mainForm.setForm(form);
            if (AllForms.isSingletonForm(form)) {
                Drawer.setSelectedItemClass(form.getClass());
            }
        }
    }

    public static void redo() {
        if (FORMS.isRedoAble()) {
            Form form = FORMS.redo();
            form.formCheck();
            form.formOpen();
            mainForm.setForm(form);
            if (AllForms.isSingletonForm(form)) {
                Drawer.setSelectedItemClass(form.getClass());
            }
        }
    }

    public static void refresh() {
        if (FORMS.getCurrent() != null) {
            FORMS.getCurrent().formRefresh();
            mainForm.refresh();
        }
    }

    public static void login() {
        Servicio.getInactivityMonitor().start();

        Drawer.setVisible(true);
        frame.getContentPane().removeAll();
        if (FORMS.isRedoAble()) {
            redo();
        } else {
            frame.getContentPane().add(getMainForm());
        }

        Drawer.setSelectedItemClass(FormDashboard.class);
        FORMS.clear();
        frame.repaint();
        frame.revalidate();
    }

    public static void logout() {
        Drawer.setVisible(false);
        frame.getContentPane().removeAll();

        UserService userService = ServiceManager.getUserService();

        // 1. Sistemde daha önce kurulum yapılmış mı? (Asenkron sor)
        userService.hasSetupCompleted().thenAccept(hasSetup -> {
            SwingUtilities.invokeLater(() -> {
                FORMS.clear(); // Geçmiş formları temizle
                if (!hasSetup) {
                    // Hiç kullanıcı yok, İLK KURULUM ekranını ekrana bas
                    SetupPanel setup = new SetupPanel();
                    setup.formCheck();
                    frame.getContentPane().add(setup);
                } else {
                    // Kullanıcı var, 6 Haneli PIN KİLİT ekranını bas
                    PinPanel pinScreen = getLogin();
                    pinScreen.formCheck();
                    frame.getContentPane().add(pinScreen);
                }
                frame.repaint();
                frame.revalidate();
            });
        });
    }

    private static MainForm getMainForm() {
        if (mainForm == null) {
            mainForm = new MainForm();
        }
        return mainForm;
    }

    private static PinPanel getLogin() {
        if (pinPanel == null) {
            pinPanel = new PinPanel();
        }
        return pinPanel;
    }

    public static void showAbout() {
        ModalDialog.showModal(frame, new SimpleModalBorder(new About(), "Hakkında"),
                ModalDialog.createOption().setAnimationEnabled(false)
        );
    }
}
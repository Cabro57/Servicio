package tr.cabro.servicio.application.system;

import javax.swing.*;

public class Form extends JPanel {
    private LookAndFeel oldTheme = UIManager.getLookAndFeel();

    public Form() {
        init();
    }

    private void init() {
    }

    public void formInit() {
    }

    public void formOpen() {
    }

    public void formRefresh() {
    }

    /**
     * Form navigasyon geçmişinden (geri/ileri) kalıcı olarak atıldığında çağrılır.
     * Formun tuttuğu Timer/liste gibi kaynakları burada serbest bırakılabilir.
     */
    public void formClose() {
    }

    protected final void formCheck() {
        if (oldTheme != UIManager.getLookAndFeel()) {
            oldTheme = UIManager.getLookAndFeel();
            SwingUtilities.updateComponentTreeUI(this);
        }
    }
}

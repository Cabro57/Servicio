package tr.cabro.servicio.application.handlers;

import javax.swing.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

public class BrandExportHandler extends TransferHandler {
    @Override
    protected Transferable createTransferable(JComponent c) {
        JList<?> list = (JList<?>) c;
        Object value = list.getSelectedValue();
        return value != null ? new StringSelection(value.toString()) : null;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return MOVE;
    }
}

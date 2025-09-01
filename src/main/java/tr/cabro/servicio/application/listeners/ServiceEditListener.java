package tr.cabro.servicio.application.listeners;

import tr.cabro.servicio.model.Process;

public interface ServiceEditListener {

    void onPartChange(double price);

    void onProcessAdded(String name, double price);

    void onProcessAdded(Process process);

    void onStatusChanged(String status);
}

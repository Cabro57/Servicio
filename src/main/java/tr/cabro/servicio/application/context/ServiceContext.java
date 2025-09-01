package tr.cabro.servicio.application.context;

import lombok.Getter;
import lombok.Setter;
import tr.cabro.servicio.model.Process;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.model.AddedPart;

import java.util.ArrayList;
import java.util.List;

@Getter
public class ServiceContext {

    @Setter
    private Service service;
    private final List<AddedPart> parts = new ArrayList<>();
    private final List<Process> processes = new ArrayList<>();

    public void setParts(List<AddedPart> newParts) {
        parts.clear();
        if (newParts != null) {
            parts.addAll(newParts);
        }
    }

    public void setProcesses(List<Process> newProcesses) {
        processes.clear();
        if (newProcesses != null) {
            processes.addAll(newProcesses);
        }
    }
}

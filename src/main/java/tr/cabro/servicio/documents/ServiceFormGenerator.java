package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrder;

import java.io.File;

/** Bir iş emrinden sabit tasarımlı bir PDF belge üreten form tiplerinin ortak arayüzü. */
public interface ServiceFormGenerator {

    File generate(WorkOrder workOrder, User shop) throws Exception;
}

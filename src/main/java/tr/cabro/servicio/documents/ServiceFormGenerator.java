package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrder;

import java.io.File;

/**
 * Bir iş emrinden sabit tasarımlı bir PDF belge üreten form tiplerinin ortak arayüzü.
 * İmzasız formlar {@link DocumentRequest}'teki imza isimlerini, termal fişler {@code format}'ı yok sayar.
 */
public interface ServiceFormGenerator {

    File generate(WorkOrder workOrder, User shop, DocumentRequest request, DocumentFormat format, File outFile) throws Exception;
}

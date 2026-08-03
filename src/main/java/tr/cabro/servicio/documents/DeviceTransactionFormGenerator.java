package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.DeviceTransaction;
import tr.cabro.servicio.model.User;

import java.io.File;

/**
 * Bir {@link DeviceTransaction} (alım/satım olayı) için sabit tasarımlı PDF üreten form
 * tiplerinin ortak arayüzü. İmzasız formlar {@code leftSignerName}/{@code rightSignerName}
 * parametrelerini yok sayar.
 */
public interface DeviceTransactionFormGenerator {

    File generate(DeviceTransaction transaction, User shop, String leftSignerName, String rightSignerName) throws Exception;
}

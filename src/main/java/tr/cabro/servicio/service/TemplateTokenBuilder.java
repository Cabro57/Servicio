package tr.cabro.servicio.service;

import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** İş emri + işletme profili verilerinden şablon (belge/WhatsApp) yer tutucu değerlerini üretir. */
public final class TemplateTokenBuilder {

    private TemplateTokenBuilder() {
    }

    public static Map<String, String> fromWorkOrder(WorkOrder wo, User shop) {
        Map<String, String> t = new HashMap<>();
        Customer c = wo.getCustomer();
        Device d = wo.getDevice();

        t.put("musteri_adi", c != null ? c.getFullName() : "-");
        t.put("musteri_telefon", c != null && c.getPhoneNumber1() != null ? PhoneHelper.formatForDisplay(c.getPhoneNumber1()) : "-");
        t.put("cihaz_bilgisi", d != null ? d.getDisplayName() : "-");
        t.put("cihaz_marka", d != null && d.getBrand() != null ? d.getBrand().getName() : "-");
        t.put("cihaz_model", d != null && d.getModel() != null ? d.getModel() : "-");
        t.put("cihaz_seri_no", d != null && d.getSerialNo() != null ? d.getSerialNo() : "-");
        t.put("servis_no", "SRV-" + wo.getId());
        t.put("ariza_aciklamasi", wo.getReportedFault() != null ? wo.getReportedFault() : "-");
        t.put("servis_durumu", wo.getServiceStatus() != null ? wo.getServiceStatus().getDisplayName() : "-");
        t.put("toplam_tutar", Format.formatPrice(wo.getTotalServiceAmount()));
        t.put("odenen_tutar", Format.formatPrice(wo.getTotalPaid()));
        t.put("kalan_tutar", Format.formatPrice(wo.getRemainingAmount()));
        t.put("teslim_tarihi", wo.getDeliveryDate() != null ? Format.formatDate(wo.getDeliveryDate()) : "-");
        t.put("bugunun_tarihi", Format.formatDate(LocalDate.now()));
        t.put("isletme_adi", shop != null && shop.getBusinessName() != null ? shop.getBusinessName() : "-");
        t.put("isletme_telefon", shop != null && shop.getPhoneNumber() != null ? PhoneHelper.formatForDisplay(shop.getPhoneNumber()) : "-");
        t.put("isletme_adres", shop != null && shop.getAddress() != null ? shop.getAddress() : "-");

        return t;
    }
}

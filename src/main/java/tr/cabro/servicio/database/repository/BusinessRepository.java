package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.Business;

import java.util.Optional;

/** Tek satırlık işletme bilgisi ({@code business_profile}, id = 1). */
@RegisterBeanMapper(Business.class)
public interface BusinessRepository {

    @SqlQuery("SELECT * FROM business_profile WHERE id = 1")
    Optional<Business> find();

    /** Satır yoksa ekler, varsa tüm alanları yazar. */
    @SqlUpdate("INSERT INTO business_profile (id, business_name, phone_number, phone_number2, email, website, address, "
            + "tax_office, tax_number, iban, bank_name, account_holder, working_hours, logo_path, updated_at) "
            + "VALUES (1, :businessName, :phoneNumber, :phoneNumber2, :email, :website, :address, "
            + ":taxOffice, :taxNumber, :iban, :bankName, :accountHolder, :workingHours, :logoPath, CURRENT_TIMESTAMP) "
            + "ON CONFLICT(id) DO UPDATE SET business_name = excluded.business_name, phone_number = excluded.phone_number, "
            + "phone_number2 = excluded.phone_number2, email = excluded.email, website = excluded.website, "
            + "address = excluded.address, tax_office = excluded.tax_office, tax_number = excluded.tax_number, "
            + "iban = excluded.iban, bank_name = excluded.bank_name, account_holder = excluded.account_holder, "
            + "working_hours = excluded.working_hours, logo_path = excluded.logo_path, updated_at = CURRENT_TIMESTAMP")
    void save(@BindBean Business business);
}

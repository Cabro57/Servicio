package tr.cabro.servicio.importer.mapper;

import tr.cabro.servicio.model.Customer;
import java.time.format.DateTimeFormatter;

public class CustomerCsvMapper extends BaseCsvMapper<Customer> {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Override
    protected int expectedFieldCount() {
        return 11;
    }

    @Override
    protected Customer mapRow(String[] fields) {
        Customer c = new Customer();
        c.setID(parseInt(fields[0]));

        // Ad soyad parçalama
        String fullName = clean(fields[1]);
        NameParts nameParts = splitFullName(fullName);
        c.setName(nameParts.name);
        c.setSurname(nameParts.surname);

        c.setId_no(clean(fields[2]));
        c.setEmail(clean(fields[3]));
        c.setPhone_number_1(normalizePhone(fields[4]));
        c.setStatus(mapStatusFromType(fields[5]));
        c.setAddress(clean(fields[7]));
        c.setNote(clean(fields[8]));
        if (!fields[9].isEmpty()) {
            c.setCreated_at(parseDateTime(fields[9], DATE_TIME_FORMATTER));
        }
        return c;
    }

    /** Ad ve soyadı ayırır */
    private NameParts splitFullName(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return new NameParts("", "");
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length > 1) {
            String surname = parts[parts.length - 1];
            String name = fullName.substring(0, fullName.lastIndexOf(' '));
            return new NameParts(name, surname);
        } else {
            return new NameParts(fullName, "");
        }
    }

    private String mapStatusFromType(String type) {
        if (type == null) return "Dikkat Et";
        switch (type.trim()) {
            case "Bireysel Müşteri": return "Normal";
            case "Kurumsal Müşteri": return "Esnaf";
            case "Sorunlu Müşteri": return "Problemli";
            default: return "Dikkat Et";
        }
    }

    /** İç sınıf: Ad-Soyad çiftini taşır */
    private static class NameParts {
        String name;
        String surname;
        NameParts(String name, String surname) {
            this.name = name;
            this.surname = surname;
        }
    }
}

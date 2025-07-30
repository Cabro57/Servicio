package tr.cabro.servicio.importer.mapper;

import tr.cabro.servicio.model.Supplier;

public class SupplierCsvMapper extends BaseCsvMapper<Supplier> {

    @Override
    protected int expectedFieldCount() {
        return 2;
    }

    @Override
    protected Supplier mapRow(String[] fields) {
        Supplier s = new Supplier();

        s.setId(parseInt(fields[0]));
        s.setName("");
        s.setBusiness_name(clean(fields[1]));

        return s;
    }
}

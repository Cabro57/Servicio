package tr.cabro.servicio.importer.mapper;

public class DefaultCsvMapper extends BaseCsvMapper<String> {

    @Override
    protected int expectedFieldCount() {
        return 2;
    }

    @Override
    protected String mapRow(String[] fields) {
        return clean(fields[1]);
    }
}

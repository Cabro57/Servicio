package tr.cabro.servicio.application.component.table;

import tr.cabro.servicio.application.tablemodal.ColumnDef.Option;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.service.ServiceManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Liste tablolarının seçim listesi (LOOKUP) filtrelerinin seçenek kaynakları. */
public final class Lookups {

    private Lookups() {}

    public static Supplier<CompletableFuture<List<Option>>> customers() {
        return () -> ServiceManager.getCustomerService().getAll().thenApply(list -> list.stream()
                .map(c -> new Option(c.getId(), label(c)))
                .collect(Collectors.toList()));
    }

    private static String label(Customer c) {
        String name = c.getFullName();
        if (c.getBusinessName() != null && !c.getBusinessName().isBlank() && !c.getBusinessName().equals(name)) {
            name += "  ·  " + c.getBusinessName();
        }
        return name;
    }

    public static Supplier<CompletableFuture<List<Option>>> suppliers() {
        return () -> ServiceManager.getSupplierService().getAll().thenApply(list -> list.stream()
                .map(s -> new Option(s.getId(),
                        s.getBusinessName() != null && !s.getBusinessName().isBlank() ? s.getBusinessName() : s.getName()))
                .collect(Collectors.toList()));
    }

    public static Supplier<CompletableFuture<List<Option>>> partCategories() {
        return () -> ServiceManager.getPartCategoryManager().getAll().thenApply(list -> list.stream()
                .map(c -> new Option(c.getId(), c.getName()))
                .collect(Collectors.toList()));
    }

    public static Supplier<CompletableFuture<List<Option>>> deviceTypes() {
        return () -> ServiceManager.getDeviceDictionaryManager().getAllTypes().thenApply(list -> list.stream()
                .map(t -> new Option(t.getId(), t.getName()))
                .collect(Collectors.toList()));
    }
}

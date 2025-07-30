package tr.cabro.servicio.service;

import lombok.Getter;

public final class ServiceManager {

    @Getter
    private static final PartService partService = new PartService();

    @Getter
    private static final RepairService repairService = new RepairService();

    @Getter
    private static final CustomerService customerService = new CustomerService();

    @Getter
    private static final SupplierService supplierService = new SupplierService();
}

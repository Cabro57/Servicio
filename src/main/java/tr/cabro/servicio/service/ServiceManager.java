package tr.cabro.servicio.service;

import lombok.Getter;
import tr.cabro.servicio.database.dao.*;

public final class ServiceManager {

    @Getter
    private static PartService partService;

    @Getter
    private static RepairService repairService;

    @Getter
    private static CustomerService customerService;

    @Getter
    private static SupplierService supplierService;

    public static void initialize() {
        CustomerDao customerDao = new CustomerDao();
        PartDao partDao = new PartDao();
        ServiceDao serviceDao = new ServiceDao();
        AddedPartDao addedPartDao = new AddedPartDao();
        SupplierDao supplierDao = new SupplierDao();

        customerService = new CustomerService(customerDao);
        partService = new PartService(partDao);
        repairService = new RepairService(serviceDao, addedPartDao);
        supplierService = new SupplierService(supplierDao);
    }
}

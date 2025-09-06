package tr.cabro.servicio.service;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.dao.ServiceDao;
import tr.cabro.servicio.database.dao.ServicePartDao;
import tr.cabro.servicio.model.AddedPart;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.model.ServiceStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RepairService {

    private final ServiceDao serviceDao;
    private final ServicePartDao servicePartDao;

    public RepairService() {
        this.serviceDao = new ServiceDao();
        this.servicePartDao = new ServicePartDao();
    }

    public boolean save(Service service, boolean update) {
        try {
            if (!update) {
                serviceDao.create(service);
            } else {
                serviceDao.update(service);
            }
            return true;
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [SAVE SERVICE] {}", String.valueOf(e));
            return false;
        }
    }

    public boolean delete(int id) {
        try {
            serviceDao.delete(id);
            return true;
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [DELETE SERVICE] {}", String.valueOf(e));
            return false;
        }
    }

    public Optional<Service> get(int id) {
        return serviceDao.getByKey(id);
    }

    public List<Service> getAll() {
        try {
            return serviceDao.getAll();
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [GET ALL SERVICES] {}", String.valueOf(e));
            return Collections.emptyList();
        }
    }

    public List<Service> getAll(int customerId) {
        try {
            return serviceDao.getByCustomerId(customerId);
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [GET BY CUSTOMER ID] {}", String.valueOf(e));
            return Collections.emptyList();
        }
    }

    public List<Service> getAll(String statusStr) {
        List<Service> services = serviceDao.getAll();

        if (statusStr == null || statusStr.isEmpty() || statusStr.equalsIgnoreCase("ALL")) {
            return services;
        }

        ServiceStatus status;
        try {
            status = ServiceStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return new ArrayList<>();
        }

        return services.stream()
                .filter(service -> service.getService_status() == status)
                .collect(Collectors.toList());
    }

    // Service Part Methods

    public boolean addPart(int serviceId, AddedPart part) {
        try {
            part.setServiceId(serviceId);
            boolean b = servicePartDao.create(part);
            if(!b) return false;

            if (!part.getBarcode().isEmpty()) {
                PartService partService = ServiceManager.getPartService();
                b = partService.increaseStock(part);
            }

            return b;
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [ADD PART TO SERVICE] {}", String.valueOf(e));
            return false;
        }
    }

    public boolean removePart(AddedPart part) {
        try {

            boolean b;

            b = servicePartDao.delete(part.getId());
            if (!b) return false;

            if (!part.getBarcode().isEmpty()) {
                PartService partService = ServiceManager.getPartService();
                b = partService.decreaseStock(part);
            }

            return b;

        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [DELETE ADDED PART] {}", String.valueOf(e));
            return false;
        }
    }

    public boolean removeParts(int serviceId) {
        try {
            List<AddedPart> parts = servicePartDao.getByServiceId(serviceId);
            if (parts == null || parts.isEmpty()) {
                return true; // silinecek parça yok
            }

            boolean allSuccess = true;

            for (AddedPart part : parts) {
                boolean deleted = servicePartDao.delete(part.getId());
                if (!deleted) {
                    Servicio.getLogger().warn("FAILED TO DELETE ADDED PART [ServiceId: {}, PartId: {}]",
                            serviceId, part.getId());
                    allSuccess = false;
                    continue;
                }

                if (!part.getBarcode().isEmpty()) {
                    PartService partService = ServiceManager.getPartService();
                    boolean stockUpdated = partService.decreaseStock(part);
                    if (!stockUpdated) {
                        Servicio.getLogger().warn("FAILED TO DECREASE STOCK [ServiceId: {}, PartId: {}, Barcode: {}]",
                                serviceId, part.getId(), part.getBarcode());
                        allSuccess = false;
                    }
                }
            }

            return allSuccess;
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [DELETE PARTS BY SERVICE ID] {}", String.valueOf(e));
            return false;
        }
    }

    public double getTotalPartsCostForService(int serviceId) {
        try {
            List<AddedPart> addedParts = servicePartDao.getByServiceId(serviceId);
            double total = 0;
            for (AddedPart ap : addedParts) {
                total += ap.getTotal(); // fiyat * adet
            }
            return total;
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [GET TOTAL PARTS COST] {}", String.valueOf(e));
            return 0.0;
        }
    }

    public List<AddedPart> getParts(int serviceId) {
        try {
            return servicePartDao.getByServiceId(serviceId);
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [GET PARTS BY SERVICE ID] {}", String.valueOf(e));
            return Collections.emptyList();
        }
    }
}

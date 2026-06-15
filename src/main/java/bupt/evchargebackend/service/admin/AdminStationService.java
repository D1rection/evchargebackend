package bupt.evchargebackend.service.admin;

import bupt.evchargebackend.dto.PileRequest;
import bupt.evchargebackend.dto.StationConfigRequest;

import java.util.List;
import java.util.Map;

public interface AdminStationService {
    Map<String, Object> getStationConfig();
    void updateStationConfig(StationConfigRequest request);
    List<Map<String, Object>> listDevices();
    Map<String, Object> addDevice(PileRequest request);
    void updateDevice(String pileId, PileRequest request);
    void deleteDevice(String pileId);
}

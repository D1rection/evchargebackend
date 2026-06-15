package bupt.evchargebackend.service.admin;

import bupt.evchargebackend.common.response.Result;
import bupt.evchargebackend.dto.PileRequest;
import bupt.evchargebackend.dto.StationConfigRequest;

import java.util.List;
import java.util.Map;

public interface AdminStationService {
    Result<Map<String, Object>> getStationConfig();
    Result<Void> updateStationConfig(StationConfigRequest request);
    Result<List<Map<String, Object>>> listDevices();
    Result<Map<String, Object>> addDevice(PileRequest request);
    Result<Void> updateDevice(String pileId, PileRequest request);
    Result<Void> deleteDevice(String pileId);
}

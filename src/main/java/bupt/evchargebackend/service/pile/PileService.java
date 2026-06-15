package bupt.evchargebackend.service.pile;

import bupt.evchargebackend.entity.pile.ChargingPile;

import java.util.List;

public interface PileService {

    List<ChargingPile> listPileStates(String pileId);

    ChargingPile powerOn(String pileId);

    ChargingPile powerOff(String pileId);

    ChargingPile start(String pileId);
}

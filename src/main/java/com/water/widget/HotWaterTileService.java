package com.water.widget;

public class HotWaterTileService extends BaseWaterTileService {
    @Override
    protected String getDidKey() { return "hot"; }

    @Override
    protected String getTileLabel() { return "热水"; }
}

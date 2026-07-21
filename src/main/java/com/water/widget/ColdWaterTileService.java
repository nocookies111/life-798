package com.water.widget;

public class ColdWaterTileService extends BaseWaterTileService {
    @Override
    protected String getDidKey() { return "cold"; }

    @Override
    protected String getTileLabel() { return "冷水"; }
}

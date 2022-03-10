package com.faforever.neroxis.generator.terrain;

import com.faforever.neroxis.generator.ElementGenerator;
import com.faforever.neroxis.map.MapParameters;
import com.faforever.neroxis.map.SCMap;
import com.faforever.neroxis.map.SymmetrySettings;
import com.faforever.neroxis.mask.BooleanMask;
import com.faforever.neroxis.mask.FloatMask;
import com.faforever.neroxis.util.DebugUtil;
import com.faforever.neroxis.util.Pipeline;
import lombok.Getter;

@Getter
public abstract strictfp class TerrainGenerator extends ElementGenerator {
    protected FloatMask heightmap;
    protected BooleanMask impassable;
    protected BooleanMask unbuildable;
    protected BooleanMask passable;
    protected BooleanMask passableLand;
    protected BooleanMask passableWater;
    protected FloatMask slope;

    protected abstract void terrainSetup();

    @Override
    public void initialize(SCMap map, long seed, MapParameters mapParameters) {
        super.initialize(map, seed, mapParameters);
        SymmetrySettings symmetrySettings = mapParameters.getSymmetrySettings();
        heightmap = new FloatMask(map.getSize() + 1, random.nextLong(), symmetrySettings, "heightmap", true);
        slope = new FloatMask(map.getSize() + 1, random.nextLong(), symmetrySettings, "slope", true);
        impassable = new BooleanMask(map.getSize() + 1, random.nextLong(), symmetrySettings, "impassable", true);
        unbuildable = new BooleanMask(map.getSize() + 1, random.nextLong(), symmetrySettings, "unbuildable", true);
        passable = new BooleanMask(map.getSize() + 1, random.nextLong(), symmetrySettings, "passable", true);
        passableLand = new BooleanMask(map.getSize() + 1, random.nextLong(), symmetrySettings, "passableLand", true);
        passableWater = new BooleanMask(map.getSize() + 1, random.nextLong(), symmetrySettings, "passableWater", true);
    }

    public void setHeightmapImage() {
        Pipeline.await(heightmap);
        DebugUtil.timedRun("com.faforever.neroxis.map.generator", "setHeightMap", () ->
                heightmap.getFinalMask().writeToImage(map.getHeightmap(), 1 / map.getHeightMapScale())
        );
    }

    @Override
    public void setupPipeline() {
        terrainSetup();
        //ensure heightmap is symmetric
        heightmap.forceSymmetry();
        passableSetup();
    }

    protected void passableSetup() {
        BooleanMask actualLand = heightmap.copyAsBooleanMask(mapParameters.getBiome().getWaterSettings().getElevation());

        slope.init(heightmap.copy().supcomGradient());
        impassable.init(slope, .7f);
        unbuildable.init(slope, .05f);

        impassable.inflate(4);

        passable.init(impassable).invert();
        passableLand.init(actualLand);
        passableWater.init(actualLand).invert();

        passable.fillEdge(8, false);
        passableLand.multiply(passable);
        passableWater.deflate(16).fillEdge(8, false);
    }
}

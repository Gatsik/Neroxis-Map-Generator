package com.faforever.neroxis.generator.terrain;

import com.faforever.neroxis.brushes.Brushes;
import com.faforever.neroxis.map.*;
import com.faforever.neroxis.util.Vector3f;

public class BasicTerrainGenerator extends TerrainGenerator {
    protected BooleanMask spawnLandMask;
    protected BooleanMask spawnPlateauMask;
    protected BooleanMask land;
    protected BooleanMask mountains;
    protected BooleanMask hills;
    protected BooleanMask valleys;
    protected BooleanMask plateaus;
    protected BooleanMask ramps;
    protected BooleanMask connections;
    protected FloatMask heightmapValleys;
    protected FloatMask heightmapHills;
    protected FloatMask heightmapPlateaus;
    protected FloatMask heightmapMountains;
    protected FloatMask heightmapLand;
    protected FloatMask heightmapOcean;
    protected FloatMask noise;

    protected int spawnSize;
    protected float waterHeight;
    protected float plateauHeight;
    protected float oceanFloor;
    protected float valleyFloor;
    protected float landHeight;
    protected float shallowWaterBrushIntensity;
    protected float deepWaterBrushIntensity;
    protected float plateauBrushDensity;
    protected float valleyBrushDensity;
    protected float hillBrushDensity;
    protected int shallowWaterBrushSize;
    protected float shallowWaterBrushDensity;
    protected int deepWaterBrushSize;
    protected float deepWaterBrushDensity;
    protected int mountainBrushSize;
    protected int plateauBrushSize;
    protected int smallFeatureBrushSize;
    protected float mountainBrushIntensity;
    protected float plateauBrushIntensity;
    protected float valleyBrushIntensity;
    protected float hillBrushIntensity;
    protected float mountainBrushDensity;

    @Override
    public void initialize(SCMap map, long seed, MapParameters mapParameters) {
        super.initialize(map, seed, mapParameters);
        SymmetrySettings symmetrySettings = mapParameters.getSymmetrySettings();
        spawnLandMask = new BooleanMask(map.getSize() + 1, random.nextLong(), symmetrySettings, "spawnLandMask", true);
        spawnPlateauMask = new BooleanMask(map.getSize() + 1, random.nextLong(), symmetrySettings, "spawnPlateauMask", true);
        land = new BooleanMask(1, random.nextLong(), symmetrySettings, "land", true);
        mountains = new BooleanMask(1, random.nextLong(), symmetrySettings, "mountains", true);
        plateaus = new BooleanMask(1, random.nextLong(), symmetrySettings, "plateaus", true);
        ramps = new BooleanMask(1, random.nextLong(), symmetrySettings, "ramps", true);
        hills = new BooleanMask(1, random.nextLong(), symmetrySettings, "hills", true);
        valleys = new BooleanMask(1, random.nextLong(), symmetrySettings, "valleys", true);
        connections = new BooleanMask(1, random.nextLong(), symmetrySettings, "connections", true);
        heightmapValleys = new FloatMask(1, random.nextLong(), symmetrySettings, "heightmapValleys", true);
        heightmapHills = new FloatMask(1, random.nextLong(), symmetrySettings, "heightmapHills", true);
        heightmapPlateaus = new FloatMask(1, random.nextLong(), symmetrySettings, "heightmapPlateaus", true);
        heightmapMountains = new FloatMask(1, random.nextLong(), symmetrySettings, "heightmapMountains", true);
        heightmapLand = new FloatMask(1, random.nextLong(), symmetrySettings, "heightmapLand", true);
        heightmapOcean = new FloatMask(1, random.nextLong(), symmetrySettings, "heightmapOcean", true);
        noise = new FloatMask(1, random.nextLong(), symmetrySettings, "noise", true);

        spawnSize = 48;
        waterHeight = mapParameters.getBiome().getWaterSettings().getElevation();
        plateauHeight = 5f;
        oceanFloor = -16f;
        valleyFloor = -5f;
        landHeight = .05f;

        mountainBrushSize = map.getSize() < 512 ? 32 : 64;
        mountainBrushDensity = map.getSize() < 512 ? .1f : .05f;
        mountainBrushIntensity = 10f;

        plateauBrushSize = 64;
        plateauBrushDensity = .16f;
        plateauBrushIntensity = 10f;

        smallFeatureBrushSize = 24;
        hillBrushIntensity = 0.5f;
        valleyBrushDensity = .72f;
        hillBrushDensity = .72f;

        shallowWaterBrushIntensity = .5f;
        shallowWaterBrushSize = 24;
        shallowWaterBrushDensity = 1f;
        deepWaterBrushIntensity = 1f;
        deepWaterBrushSize = 64;
        deepWaterBrushDensity = .065f;
    }

    @Override
    protected void terrainSetup() {
        spawnMaskSetup();
        teamConnectionsSetup();
        landSetup();
        plateausSetup();
        mountainSetup();
        spawnTerrainSetup();
        if (mapParameters.getSymmetrySettings().getSpawnSymmetry().isPerfectSymmetry()) {
            setupHeightmapPipeline();
        } else {
            setupSimpleHeightmapPipeline();
        }
    }

    protected void spawnMaskSetup() {
        map.getSpawns().forEach(spawn -> {
            Vector3f location = spawn.getPosition();
            spawnLandMask.fillCircle(location, spawnSize, true);
        });

        if (random.nextFloat() < mapParameters.getPlateauDensity()) {
            map.getSpawns().forEach(spawn -> {
                Vector3f location = spawn.getPosition();
                spawnPlateauMask.fillCircle(location, spawnSize, true);
            });
        }
    }

    protected void teamConnectionsSetup() {
        float maxStepSize = map.getSize() / 128f;
        int minMiddlePoints = 1;
        int maxMiddlePoints = 2;
        int numTeamConnections = (int) ((mapParameters.getRampDensity() + mapParameters.getPlateauDensity() + (1 - mapParameters.getMountainDensity())) / 3 * 3 + 1);
        int numTeammateConnections = 1;
        connections.setSize(map.getSize() + 1);

        connectTeamsAroundCenter(connections, minMiddlePoints, maxMiddlePoints, numTeamConnections, maxStepSize, 32);
        connectTeammates(connections, maxMiddlePoints, numTeammateConnections, maxStepSize);
    }

    protected void landSetup() {
        float landDensityMax = .9f;
        float landDensityMin = .8f;
        float landDensityRange = landDensityMax - landDensityMin;
        float scaledLandDensity = mapParameters.getLandDensity() * landDensityRange + landDensityMin;
        int mapSize = map.getSize();
        land.setSize(mapSize / 16);

        land.randomize(scaledLandDensity).blur(2, .75f).erode(.5f, SymmetryType.TERRAIN, mapSize / 256);
        land.setSize(mapSize / 4);
        land.dilute(.5f, SymmetryType.TERRAIN, mapSize / 128);
        land.setSize(mapSize + 1);
        land.blur(8, .75f);

        if (mapSize <= 512) {
            land.combine(connections.copy().inflate(mountainBrushSize / 8f).blur(12, .125f));
        }
    }

    protected void plateausSetup() {
        float plateauDensityMax = .7f;
        float plateauDensityMin = .6f;
        float plateauDensityRange = plateauDensityMax - plateauDensityMin;
        float scaledPlateauDensity = mapParameters.getPlateauDensity() * plateauDensityRange + plateauDensityMin;
        plateaus.setSize(map.getSize() / 16);

        plateaus.randomize(scaledPlateauDensity).blur(2, .75f).setSize(map.getSize() / 4);
        plateaus.dilute(.5f, SymmetryType.TERRAIN, map.getSize() / 128);
        plateaus.setSize(map.getSize() + 1);
        plateaus.blur(16, .75f);
    }

    protected void mountainSetup() {
        mountains.setSize(map.getSize() / 4);

        if (random.nextBoolean()) {
            mountains.progressiveWalk((int) (mapParameters.getMountainDensity() * 100 / mapParameters.getSymmetrySettings().getTerrainSymmetry().getNumSymPoints()), map.getSize() / 16);
        } else {
            mountains.randomWalk((int) (mapParameters.getMountainDensity() * 100 / mapParameters.getSymmetrySettings().getTerrainSymmetry().getNumSymPoints()), map.getSize() / 16);
        }
        mountains.dilute(.5f, SymmetryType.TERRAIN, 2);
        mountains.setSize(map.getSize() + 1);
    }

    protected void spawnTerrainSetup() {
        spawnPlateauMask.setSize(map.getSize() / 4);
        spawnPlateauMask.erode(.5f, SymmetryType.SPAWN, 4).dilute(.5f, SymmetryType.SPAWN, 8);
        spawnPlateauMask.erode(.5f, SymmetryType.SPAWN).setSize(map.getSize() + 1);
        spawnPlateauMask.blur(4);

        spawnLandMask.setSize(map.getSize() / 4);
        spawnLandMask.erode(.25f, SymmetryType.SPAWN, map.getSize() / 128).dilute(.5f, SymmetryType.SPAWN, 4);
        spawnLandMask.erode(.5f, SymmetryType.SPAWN).setSize(map.getSize() + 1);
        spawnLandMask.blur(4);

        plateaus.minus(spawnLandMask).combine(spawnPlateauMask);
        land.combine(spawnLandMask).combine(spawnPlateauMask);
        if (map.getSize() > 512 && mapParameters.getSymmetrySettings().getSpawnSymmetry().getNumSymPoints() <= 4) {
            land.combine(spawnLandMask).combine(spawnPlateauMask).inflate(16).deflate(16).setSize(map.getSize() / 8);
            land.erode(.5f, SymmetryType.SPAWN, 10).combine(spawnLandMask.copy().setSize(map.getSize() / 8)).combine(spawnPlateauMask.copy().setSize(map.getSize() / 8))
                    .blur(4, .75f).dilute(.5f, SymmetryType.SPAWN, 5).setSize(map.getSize() + 1);
            land.blur(8, .75f);
        } else {
            land.dilute(.25f, SymmetryType.SPAWN, 16).blur(2);
        }

        ensureSpawnTerrain();

        mountains.intersect(mapParameters.getLandDensity() < .25f ? land.copy().deflate(24) : land);
    }

    protected void ensureSpawnTerrain() {
        mountains.minus(connections.copy().inflate(mountainBrushSize / 2f).blur(12, .125f));
        mountains.minus(spawnLandMask.copy().inflate(mountainBrushSize / 2f));

        plateaus.intersect(land).minus(spawnLandMask).combine(spawnPlateauMask);
        land.combine(plateaus).combine(spawnLandMask).combine(spawnPlateauMask);
    }

    protected void setupHeightmapPipeline() {
        int mapSize = map.getSize();
        int numBrushes = Brushes.GENERATOR_BRUSHES.size();

        String brush5 = Brushes.GENERATOR_BRUSHES.get(random.nextInt(numBrushes));

        setupMountainHeightmapPipeline();
        setupPlateauHeightmapPipeline();
        setupSmallFeatureHeightmapPipeline();
        initRamps();

        BooleanMask water = land.copy().invert();
        BooleanMask deepWater = water.copy().deflate(32);

        heightmap.setSize(mapSize + 1);
        heightmapLand.setSize(mapSize + 1);
        heightmapOcean.setSize(mapSize + 1);
        noise.setSize(mapSize / 128);

        heightmapOcean.addDistance(land, -.45f).clampMin(oceanFloor).useBrushWithinAreaWithDensity(water.deflate(8).minus(deepWater), brush5, shallowWaterBrushSize, shallowWaterBrushDensity, shallowWaterBrushIntensity, false)
                .useBrushWithinAreaWithDensity(deepWater, brush5, deepWaterBrushSize, deepWaterBrushDensity, deepWaterBrushIntensity, false).clampMax(0f).blur(4, deepWater).blur(1);

        heightmapLand.add(heightmapHills).add(heightmapValleys).add(heightmapMountains).add(landHeight)
                .add(heightmapPlateaus).setToValue(spawnLandMask, landHeight).setToValue(spawnPlateauMask, plateauHeight + landHeight)
                .blur(1, spawnLandMask.copy().inflate(4)).blur(1, spawnPlateauMask.copy().inflate(4)).add(heightmapOcean);

        noise.addWhiteNoise(plateauHeight / 2).resample(mapSize / 64);
        noise.addWhiteNoise(plateauHeight / 2).resample(mapSize + 1);
        noise.addWhiteNoise(1)
                .subtractAvg().clampMin(0f).setToValue(land.copy().invert().inflate(16), 0f)
                .blur(mapSize / 16, spawnLandMask.copy().inflate(8))
                .blur(mapSize / 16, spawnPlateauMask.copy().inflate(8)).blur(mapSize / 16);

        heightmap.add(heightmapLand).add(waterHeight).add(noise);

        blurRamps();
    }

    protected void setupSimpleHeightmapPipeline() {
        int mapSize = map.getSize();
        float waterHeight = mapParameters.getBiome().getWaterSettings().getElevation();

        BooleanMask symmetryLimits = new BooleanMask(mapSize + 1, random.nextLong(), mapParameters.getSymmetrySettings(), "symmetryLimits", true);
        symmetryLimits.fillCircle((mapSize + 1) / 2f, (mapSize + 1) / 2f, (mapSize - 32) / 2f, true).setSize(mapSize / 8);
        symmetryLimits.inflate(1).erode(.5f, SymmetryType.SPAWN, 6).inflate(2);
        symmetryLimits.setSize(mapSize + 1);
        symmetryLimits.inflate(4);

        heightmap.setSize(mapSize + 1);
        heightmapMountains.setSize(mapSize + 1);
        heightmapPlateaus.setSize(mapSize + 1);
        heightmapLand.setSize(mapSize + 1);
        heightmapOcean.setSize(mapSize + 1);
        noise.setSize(mapSize / 128);

        land.intersect(symmetryLimits);
        plateaus.intersect(symmetryLimits);
        mountains.intersect(symmetryLimits);

        heightmapPlateaus.addDistance(plateaus.dilute(1, SymmetryType.SPAWN).inflate(1).invert(), 1).clampMax(plateauHeight);
        heightmapMountains.addDistance(mountains.dilute(1, SymmetryType.SPAWN).inflate(1).invert(), 1f);
        heightmapOcean.addDistance(land, -.45f).clampMin(oceanFloor);

        BooleanMask paintedPlateaus = new BooleanMask(heightmapPlateaus, plateauHeight - 3, random.nextLong(), "paintedPlateaus");
        BooleanMask paintedMountains = new BooleanMask(heightmapMountains, plateauHeight / 2, random.nextLong(), "paintedMountains");

        land.combine(paintedPlateaus);
        plateaus.replace(paintedPlateaus);
        plateaus.minus(spawnLandMask).combine(spawnPlateauMask);
        mountains.replace(paintedMountains);
        land.combine(paintedMountains);

        heightmapPlateaus.add(plateaus, 3f).clampMax(plateauHeight).blur(1, plateaus);

        initRamps();

        heightmapLand.add(heightmapMountains).add(landHeight)
                .setToValue(spawnLandMask, landHeight).add(heightmapPlateaus).setToValue(spawnPlateauMask, plateauHeight + landHeight)
                .blur(1, spawnPlateauMask.copy().inflate(4)).add(heightmapOcean);

        heightmap.add(heightmapLand).add(waterHeight);

        blurRamps();
    }

    private void blurRamps() {
        heightmap.blur(8, ramps.copy().acid(.001f, 4).erode(.25f, SymmetryType.SPAWN, 4))
                .blur(6, ramps.copy().inflate(2).acid(.01f, 4).erode(.25f, SymmetryType.SPAWN, 4))
                .blur(4, ramps.copy().inflate(4))
                .blur(2, ramps.copy().inflate(8))
                .clampMin(0f).clampMax(255f);
    }

    protected void initRamps() {
        float maxStepSize = map.getSize() / 128f;
        int maxMiddlePoints = 2;
        int numPaths = (int) (mapParameters.getRampDensity() * 20) / mapParameters.getSymmetrySettings().getTerrainSymmetry().getNumSymPoints();
        int bound = map.getSize() / 4;
        ramps.setSize(map.getSize() + 1);

        if (map.getSize() >= 512) {
            pathInEdgeBounds(ramps, maxStepSize, numPaths, maxMiddlePoints, bound, (float) (StrictMath.PI / 2));
        } else {
            pathInEdgeBounds(ramps, maxStepSize, numPaths / 4, maxMiddlePoints, bound, (float) (StrictMath.PI / 2));
        }

        ramps.minus(connections.copy().inflate(32)).inflate(maxStepSize * 2).intersect(plateaus.copy().outline())
                .combine(connections.copy().inflate(maxStepSize * 2).intersect(plateaus.copy().outline()))
                .minus(mountains).inflate(8);
    }

    protected void setupMountainHeightmapPipeline() {
        String brush = Brushes.GENERATOR_BRUSHES.get(random.nextInt(Brushes.GENERATOR_BRUSHES.size()));

        heightmapMountains.setSize(map.getSize() + 1);
        heightmapMountains.useBrushWithinAreaWithDensity(mountains, brush, mountainBrushSize, mountainBrushDensity, mountainBrushIntensity, false);

        BooleanMask paintedMountains = new BooleanMask(heightmapMountains, plateauHeight / 2, random.nextLong(), "paintedMountains");

        mountains.replace(paintedMountains);
        land.combine(paintedMountains);

        heightmapMountains.blur(4, mountains.copy().inflate(32).minus(mountains.copy().inflate(4)));
    }

    protected void setupPlateauHeightmapPipeline() {
        String brush = Brushes.GENERATOR_BRUSHES.get(random.nextInt(Brushes.GENERATOR_BRUSHES.size()));

        heightmapPlateaus.setSize(map.getSize() + 1);
        heightmapPlateaus.useBrushWithinAreaWithDensity(plateaus, brush, plateauBrushSize, plateauBrushDensity, plateauBrushIntensity, false).clampMax(plateauHeight);

        BooleanMask paintedPlateaus = new BooleanMask(heightmapPlateaus, plateauHeight - 3, random.nextLong(), "paintedPlateaus");

        land.combine(paintedPlateaus);
        plateaus.replace(paintedPlateaus);
        plateaus.minus(spawnLandMask).combine(spawnPlateauMask);

        heightmapPlateaus.add(plateaus, 3f).clampMax(plateauHeight).blur(1, plateaus);

        BooleanMask plateauBase = new BooleanMask(heightmapPlateaus, 1f, random.nextLong(), "plateauBase");

        heightmapPlateaus.blur(1, plateauBase.copy().outline().inflate(8).minus(plateauBase));
    }

    protected void setupSmallFeatureHeightmapPipeline() {
        int numSymPoints = mapParameters.getSymmetrySettings().getSpawnSymmetry().getNumSymPoints();
        String brushValley = Brushes.GENERATOR_BRUSHES.get(random.nextInt(Brushes.GENERATOR_BRUSHES.size()));
        String brushHill = Brushes.GENERATOR_BRUSHES.get(random.nextInt(Brushes.GENERATOR_BRUSHES.size()));

        heightmapValleys.setSize(map.getSize() + 1);
        heightmapHills.setSize(map.getSize() + 1);

        hills.setSize(map.getSize() / 4);
        valleys.setSize(map.getSize() / 4);

        hills.randomWalk(random.nextInt(4) + 1, random.nextInt(map.getSize() / 2) / numSymPoints).dilute(.5f, SymmetryType.SPAWN, 2)
                .setSize(map.getSize() + 1);
        hills.intersect(land.copy().deflate(8)).minus(plateaus.copy().outline().inflate(8)).minus(spawnLandMask);
        valleys.randomWalk(random.nextInt(4), random.nextInt(map.getSize() / 2) / numSymPoints).dilute(.5f, SymmetryType.SPAWN, 4)
                .setSize(map.getSize() + 1);
        valleys.intersect(plateaus.copy().deflate(8)).minus(spawnPlateauMask);

        valleyBrushIntensity = -0.35f;
        heightmapValleys.useBrushWithinAreaWithDensity(valleys, brushValley, smallFeatureBrushSize, valleyBrushDensity, valleyBrushIntensity, false)
                .clampMin(valleyFloor);
        heightmapHills.useBrushWithinAreaWithDensity(hills.combine(mountains.copy().outline().inflate(4).acid(.01f, 4)), brushHill, smallFeatureBrushSize, hillBrushDensity, hillBrushIntensity, false);
    }
}

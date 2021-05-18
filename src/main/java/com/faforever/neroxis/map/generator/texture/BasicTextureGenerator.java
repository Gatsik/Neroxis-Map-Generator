package com.faforever.neroxis.map.generator.texture;

import com.faforever.neroxis.map.MapParameters;
import com.faforever.neroxis.map.SCMap;
import com.faforever.neroxis.map.SymmetrySettings;
import com.faforever.neroxis.map.generator.PreviewGenerator;
import com.faforever.neroxis.map.generator.terrain.TerrainGenerator;
import com.faforever.neroxis.map.mask.BooleanMask;
import com.faforever.neroxis.map.mask.FloatMask;
import com.faforever.neroxis.util.ImageUtils;
import com.faforever.neroxis.util.Pipeline;
import com.faforever.neroxis.util.Util;

import java.io.IOException;

public strictfp class BasicTextureGenerator extends TextureGenerator {
    protected BooleanMask realLand;
    protected BooleanMask realPlateaus;
    protected FloatMask accentGroundTexture;
    protected FloatMask waterBeachTexture;
    protected FloatMask accentSlopesTexture;
    protected FloatMask accentPlateauTexture;
    protected FloatMask slopesTexture;
    protected FloatMask steepHillsTexture;
    protected FloatMask rockTexture;
    protected FloatMask accentRockTexture;
    protected FloatMask accentGroundPreviewTexture;
    protected FloatMask waterBeachPreviewTexture;
    protected FloatMask accentSlopesPreviewTexture;
    protected FloatMask accentPlateauPreviewTexture;
    protected FloatMask slopesPreviewTexture;
    protected FloatMask steepHillsPreviewTexture;
    protected FloatMask rockPreviewTexture;
    protected FloatMask accentRockPreviewTexture;
    protected FloatMask heightmapPreview;
    protected FloatMask reflectance;

    @Override
    public void initialize(SCMap map, long seed, MapParameters mapParameters, TerrainGenerator terrainGenerator) {
        super.initialize(map, seed, mapParameters, terrainGenerator);
        SymmetrySettings symmetrySettings = mapParameters.getSymmetrySettings();
        realLand = new BooleanMask(heightmap, mapParameters.getBiome().getWaterSettings().getElevation(), random.nextLong(), "realLand");
        realPlateaus = new BooleanMask(heightmap, mapParameters.getBiome().getWaterSettings().getElevation() + 3f, random.nextLong(), "realPlateaus");
        accentGroundTexture = new FloatMask(1, random.nextLong(), symmetrySettings, "accentGroundTexture", true);
        waterBeachTexture = new FloatMask(1, random.nextLong(), symmetrySettings, "waterBeachTexture", true);
        accentSlopesTexture = new FloatMask(1, random.nextLong(), symmetrySettings, "accentSlopesTexture", true);
        accentPlateauTexture = new FloatMask(1, random.nextLong(), symmetrySettings, "accentPlateauTexture", true);
        slopesTexture = new FloatMask(1, random.nextLong(), symmetrySettings, "slopesTexture", true);
        steepHillsTexture = new FloatMask(1, random.nextLong(), symmetrySettings, "steepHillsTexture", true);
        rockTexture = new FloatMask(1, random.nextLong(), symmetrySettings, "rockTexture", true);
        accentRockTexture = new FloatMask(1, random.nextLong(), symmetrySettings, "accentRockTexture", true);
    }

    @Override
    public void setupPipeline() {
        setupTexturePipeline();
        setupPreviewPipeline();
    }

    @Override
    public void setTextures() {
        Pipeline.await(accentGroundTexture, accentPlateauTexture, slopesTexture, accentSlopesTexture, steepHillsTexture, waterBeachTexture, rockTexture, accentRockTexture, normals);
        Util.timedRun("com.faforever.neroxis.map.generator", "generateTextures", () -> {
            map.setTextureMasksScaled(map.getTextureMasksLow(), accentGroundTexture.getFinalMask(), accentPlateauTexture.getFinalMask(), slopesTexture.getFinalMask(), accentSlopesTexture.getFinalMask());
            map.setTextureMasksScaled(map.getTextureMasksHigh(), steepHillsTexture.getFinalMask(), waterBeachTexture.getFinalMask(), rockTexture.getFinalMask(), accentRockTexture.getFinalMask());
        });
    }

    @Override
    public void setCompressedNormal() {
        Pipeline.await(normals);
        Util.timedRun("com.faforever.neroxis.map.generator", "setCompressedNormal", () -> {
            map.setCompressedNormal(ImageUtils.compressNormal(normals.getFinalMask()));
        });
    }

    @Override
    public void generatePreview() {
        Pipeline.await(accentGroundPreviewTexture, accentPlateauPreviewTexture, slopesPreviewTexture,
                accentSlopesPreviewTexture, steepHillsPreviewTexture, waterBeachPreviewTexture, rockPreviewTexture,
                accentRockPreviewTexture, reflectance, heightmapPreview);
        Util.timedRun("com.faforever.neroxis.map.generator", "generatePreview", () -> {
            try {
                PreviewGenerator.generatePreview(heightmapPreview.getFinalMask(), reflectance.getFinalMask(), map,
                        accentGroundPreviewTexture.getFinalMask(), accentPlateauPreviewTexture.getFinalMask(), slopesPreviewTexture.getFinalMask(), accentSlopesPreviewTexture.getFinalMask(),
                        steepHillsPreviewTexture.getFinalMask(), waterBeachPreviewTexture.getFinalMask(), rockPreviewTexture.getFinalMask(), accentRockPreviewTexture.getFinalMask());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    protected void setupTexturePipeline() {
        BooleanMask flat = new BooleanMask(slope, .05f, random.nextLong(), "flat").invert();
        BooleanMask slopes = new BooleanMask(slope, .15f, random.nextLong(), "slopes");
        BooleanMask accentSlopes = new BooleanMask(slope, .55f, random.nextLong(), "accentSlopes").invert().subtract(flat);
        BooleanMask steepHills = new BooleanMask(slope, .55f, random.nextLong(), "steepHills");
        BooleanMask rock = new BooleanMask(slope, .75f, random.nextLong(), "rock");
        BooleanMask accentRock = new BooleanMask(slope, .75f, random.nextLong(), "accentRock").inflate(2f);

        int textureSize = mapParameters.getMapSize() + 1;
        int mapSize = mapParameters.getMapSize();
        accentGroundTexture.setSize(textureSize).addPerlinNoise(mapSize / 16, 1f).setToValue(realLand.copy().invert(), 0f).blur(2);
        accentPlateauTexture.setSize(textureSize).addPerlinNoise(mapSize / 16, 1f).setToValue(realPlateaus.copy().invert(), 0f).blur(4);
        slopesTexture.init(slopes, 0f, 1f).blur(8).add(slopes, .75f).blur(4).clampMax(1f);
        accentSlopesTexture.setSize(textureSize).addPerlinNoise(mapSize / 32, .5f).setToValue(accentSlopes.copy().invert(), 0f).blur(4);
        steepHillsTexture.setSize(textureSize).addPerlinNoise(mapSize / 8, 1f).setToValue(steepHills.copy().invert(), 0f).blur(8);
        waterBeachTexture.init(realLand.copy().invert().inflate(12).subtract(realPlateaus), 0f, 1f).blur(12);
        rockTexture.init(rock, 0f, 1f).blur(4).add(rock, 1f).blur(2).clampMax(1f);
        accentRockTexture.setSize(textureSize).addPerlinNoise(mapSize / 16, 1f).setToValue(accentRock.copy().invert(), 0f).blur(2);
    }

    protected void setupPreviewPipeline() {
        accentGroundPreviewTexture = accentGroundTexture.copy().resample(PreviewGenerator.PREVIEW_SIZE);
        accentPlateauPreviewTexture = accentPlateauTexture.copy().resample(PreviewGenerator.PREVIEW_SIZE);
        slopesPreviewTexture = slopesTexture.copy().resample(PreviewGenerator.PREVIEW_SIZE);
        accentSlopesPreviewTexture = accentSlopesTexture.copy().resample(PreviewGenerator.PREVIEW_SIZE);
        steepHillsPreviewTexture = steepHillsTexture.copy().resample(PreviewGenerator.PREVIEW_SIZE);
        waterBeachPreviewTexture = waterBeachTexture.copy().resample(PreviewGenerator.PREVIEW_SIZE);
        rockPreviewTexture = rockTexture.copy().resample(PreviewGenerator.PREVIEW_SIZE);
        accentRockPreviewTexture = accentRockTexture.copy().resample(PreviewGenerator.PREVIEW_SIZE);
        heightmapPreview = heightmap.copy().resample(PreviewGenerator.PREVIEW_SIZE);
        reflectance = heightmap.copy().getNormalMask(8f).resample(PreviewGenerator.PREVIEW_SIZE).dot(map.getBiome().getLightingSettings().getSunDirection()).add(1f).divide(2f);
    }
}

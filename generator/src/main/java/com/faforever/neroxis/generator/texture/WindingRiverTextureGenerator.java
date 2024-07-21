package com.faforever.neroxis.generator.texture;

import com.faforever.neroxis.biomes.Biome;
import com.faforever.neroxis.biomes.BiomeName;
import com.faforever.neroxis.biomes.Biomes;

public class WindingRiverTextureGenerator extends LegacyTextureGenerator {

    @Override
    public Biome loadBiome() {
        return Biomes.loadBiome(BiomeName.WINDINGRIVER);
    }
}
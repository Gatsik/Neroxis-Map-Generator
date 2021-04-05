package neroxis.generator.terrain;

import neroxis.map.SymmetrySettings;
import neroxis.map.SymmetryType;

public strictfp abstract class PathedPlateauTerrainGenerator extends BasicTerrainGenerator {

    @Override
    protected void plateausSetup() {
        SymmetrySettings symmetrySettings = mapParameters.getSymmetrySettings();
        int mapSize = map.getSize();
        float maxStepSize = mapSize / 128f;
        int maxMiddlePoints = 16;
        int numPaths = (int) (12 * mapParameters.getPlateauDensity()) / symmetrySettings.getSpawnSymmetry().getNumSymPoints();
        int bound = 0;
        plateaus.setSize(mapSize + 1);

        pathInCenterBounds(plateaus, maxStepSize, numPaths, maxMiddlePoints, bound, (float) (StrictMath.PI / 2));
        plateaus.inflate(mapSize / 256f).setSize(mapSize / 4);
        plateaus.grow(.5f, SymmetryType.TERRAIN, 4).setSize(mapSize + 1);
        plateaus.smooth(12);
    }
}

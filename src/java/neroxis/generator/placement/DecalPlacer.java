package neroxis.generator.placement;

import neroxis.map.BinaryMask;
import neroxis.map.Decal;
import neroxis.map.SCMap;
import neroxis.map.SymmetryType;
import neroxis.util.Vector2f;
import neroxis.util.Vector3f;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public strictfp class DecalPlacer {
    private final SCMap map;
    private final Random random;

    public DecalPlacer(SCMap map, long seed) {
        this.map = map;
        random = new Random(seed);
    }

    public void placeDecals(BinaryMask spawnMask, String[] paths, float minSeparation, float maxSeparation, float minScale, float maxScale) {
        if (paths != null && paths.length > 0) {
            BinaryMask spawnMaskCopy = new BinaryMask(spawnMask, random.nextLong());
            spawnMaskCopy.limitToSymmetryRegion();
            LinkedList<Vector2f> coordinates = spawnMaskCopy.getRandomCoordinates(minSeparation, maxSeparation);
            coordinates.forEach((location) -> {
                float scale = random.nextFloat() * (maxScale - minScale) + minScale;
                location.add(.5f, .5f);
                Vector3f rotation = new Vector3f(0f, random.nextFloat() * (float) StrictMath.PI, 0f);
                Decal decal = new Decal(paths[random.nextInt(paths.length)], location, rotation, scale, 1000 * map.getSize() / 512f);
                map.addDecal(decal);
                List<Vector2f> symmetryPoints = spawnMask.getSymmetryPoints(decal.getPosition(), SymmetryType.SPAWN);
                symmetryPoints.forEach(Vector2f::roundToNearestHalfPoint);
                ArrayList<Float> symmetryRotation = spawnMask.getSymmetryRotation(decal.getRotation().getY());
                for (int i = 0; i < symmetryPoints.size(); i++) {
                    Vector3f symVectorRotation = new Vector3f(decal.getRotation().getX(), symmetryRotation.get(i), decal.getRotation().getZ());
                    Decal symDecal = new Decal(decal.getPath(), symmetryPoints.get(i), symVectorRotation, scale, decal.getCutOffLOD());
                    map.addDecal(symDecal);
                }
            });
        }
    }
}
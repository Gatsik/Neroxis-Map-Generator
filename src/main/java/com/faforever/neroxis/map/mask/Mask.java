package com.faforever.neroxis.map.mask;

import com.faforever.neroxis.map.Symmetry;
import com.faforever.neroxis.map.SymmetrySettings;
import com.faforever.neroxis.map.SymmetryType;
import com.faforever.neroxis.util.*;
import lombok.Getter;
import lombok.Setter;

import java.awt.image.BufferedImage;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
public strictfp abstract class Mask<T, U extends Mask<T, U>> {
    @Getter
    protected final SymmetrySettings symmetrySettings;
    @Getter
    private final String name;
    protected final Random random;
    protected int plannedSize;
    @Getter
    @Setter
    private boolean parallel;
    protected T[][] mask;
    @Getter
    @Setter
    private boolean visualDebug;
    @Getter
    @Setter
    private String visualName;

    protected Mask(int size, Long seed, SymmetrySettings symmetrySettings, String name) {
        this(size, seed, symmetrySettings, name, false);
    }

    protected Mask(int size, Long seed, SymmetrySettings symmetrySettings, String name, boolean parallel) {
        this.symmetrySettings = symmetrySettings;
        this.name = name;
        this.mask = getEmptyMask(size);
        this.plannedSize = size;
        this.parallel = parallel;
        if (seed != null) {
            this.random = new Random(seed);
        } else {
            this.random = null;
        }
        enqueue(() -> {
        });
    }

    public Mask(U other, Long seed) {
        this(other, seed, null);
    }

    public Mask(U other, Long seed, String name) {
        this(other.getSize(), seed, other.getSymmetrySettings(), name, other.isParallel());
        init(other);
    }

    public abstract BufferedImage writeToImage(BufferedImage image);

    public abstract BufferedImage toImage();

    public abstract String toHash() throws NoSuchAlgorithmException;

    protected abstract T[][] getEmptyMask(int size);

    protected abstract T getZeroValue();

    public abstract U copy();

    public abstract U init(U other);

    public abstract U clear();

    public abstract U blur(int size);

    public U interpolate() {
        return blur(1);
    }

    protected static int getShiftedValue(int val, int offset, int size, boolean wrapEdges) {
        return wrapEdges ? (val + offset + size) % size : val + offset - 1;
    }

    public T get(Vector3 location) {
        return get((int) location.getX(), (int) location.getZ());
    }

    public T get(Vector2 location) {
        return get((int) location.getX(), (int) location.getY());
    }

    public T get(int x, int y) {
        return mask[x][y];
    }

    public void set(Vector3 location, T value) {
        set((int) location.getX(), (int) location.getZ(), value);
    }

    public void set(Vector2 location, T value) {
        set((int) location.getX(), (int) location.getY(), value);
    }

    public void set(int x, int y, T value) {
        mask[x][y] = value;
    }

    public int getSize() {
        if (parallel && !Pipeline.isStarted()) {
            return plannedSize;
        } else {
            return mask[0].length;
        }
    }

    public int getImmediateSize() {
        return mask[0].length;
    }

    public U setSize(int newSize) {
        int size = getSize();
        if (newSize != size) {
            plannedSize = newSize;
            enqueue(() -> {
                if (size < newSize) {
                    enlarge(newSize);
                } else {
                    shrink(newSize);
                }
            });
        }
        return (U) this;
    }

    public U resample(int newSize) {
        int size = getSize();
        if (newSize != size) {
            plannedSize = newSize;
            enqueue(() -> {
                if (size < newSize) {
                    interpolate(newSize);
                } else {
                    decimate(newSize);
                }
            });
        }
        return (U) this;
    }

    public U setToValue(BooleanMask other, T val) {
        enqueue(dependencies -> {
            BooleanMask source = (BooleanMask) dependencies.get(0);
            assertCompatibleMask(source);
            set((x, y) -> source.get(x, y) ? val : get(x, y));
        }, other);
        return (U) this;
    }

    public U replaceValues(BooleanMask area, U values) {
        enqueue(dependencies -> {
            BooleanMask placement = (BooleanMask) dependencies.get(0);
            U source = (U) dependencies.get(1);
            assertCompatibleMask(source);
            set((x, y) -> placement.get(x, y) ? source.get(x, y) : get(x, y));
        }, area, values);
        return (U) this;
    }

    public boolean inBounds(Vector2 location) {
        return inBounds((int) location.getX(), (int) location.getY());
    }

    public boolean inBounds(int x, int y) {
        int size = getSize();
        return x >= 0 && x < size && y >= 0 && y < size;
    }

    public boolean onBoundary(Vector2 location) {
        return onBoundary((int) location.getX(), (int) location.getY());
    }

    public boolean onBoundary(int x, int y) {
        int size = getSize();
        return x == 0 || x == size - 1 || y == 0 || y == size - 1;
    }

    public List<Vector2> getSymmetryPoints(Vector3 v, SymmetryType symmetryType) {
        return getSymmetryPoints(new Vector2(v), symmetryType);
    }

    public List<Vector2> getSymmetryPoints(Vector2 v, SymmetryType symmetryType) {
        return getSymmetryPoints(v.getX(), v.getY(), symmetryType);
    }

    public List<Vector2> getSymmetryPoints(float x, float y, SymmetryType symmetryType) {
        List<Vector2> symmetryPoints = getSymmetryPointsWithOutOfBounds(x, y, symmetryType);
        symmetryPoints.removeIf(point -> !inBounds(point));
        return symmetryPoints;
    }

    public List<Vector2> getSymmetryPointsWithOutOfBounds(Vector3 v, SymmetryType symmetryType) {
        return getSymmetryPointsWithOutOfBounds(new Vector2(v), symmetryType);
    }

    public List<Vector2> getSymmetryPointsWithOutOfBounds(Vector2 v, SymmetryType symmetryType) {
        return getSymmetryPointsWithOutOfBounds(v.getX(), v.getY(), symmetryType);
    }

    public List<Vector2> getSymmetryPointsWithOutOfBounds(float x, float y, SymmetryType symmetryType) {
        Symmetry symmetry = symmetrySettings.getSymmetry(symmetryType);
        int numSymPoints = symmetry.getNumSymPoints();
        List<Vector2> symmetryPoints = new ArrayList<>(numSymPoints - 1);
        int size = getSize();
        switch (symmetry) {
            case POINT2:
                symmetryPoints.add(new Vector2(size - x - 1, size - y - 1));
                break;
            case POINT4:
                symmetryPoints.add(new Vector2(size - x - 1, size - y - 1));
                symmetryPoints.add(new Vector2(y, size - x - 1));
                symmetryPoints.add(new Vector2(size - y - 1, x));
                break;
            case POINT6:
            case POINT8:
            case POINT10:
            case POINT12:
            case POINT14:
            case POINT16:
                symmetryPoints.add(new Vector2(size - x - 1, size - y - 1));
                for (int i = 1; i < numSymPoints / 2; i++) {
                    float angle = (float) (2 * StrictMath.PI * i / numSymPoints);
                    Vector2 rotated = getRotatedPoint(x, y, angle);
                    symmetryPoints.add(rotated);
                    Vector2 antiRotated = getRotatedPoint(x, y, (float) (angle + StrictMath.PI));
                    symmetryPoints.add(antiRotated);
                }
                break;
            case POINT3:
            case POINT5:
            case POINT7:
            case POINT9:
            case POINT11:
            case POINT13:
            case POINT15:
                for (int i = 1; i < numSymPoints; i++) {
                    Vector2 rotated = getRotatedPoint(x, y, (float) (2 * StrictMath.PI * i / numSymPoints));
                    symmetryPoints.add(rotated);
                }
                break;
            case X:
                symmetryPoints.add(new Vector2(size - x - 1, y));
                break;
            case Z:
                symmetryPoints.add(new Vector2(x, size - y - 1));
                break;
            case XZ:
                symmetryPoints.add(new Vector2(y, x));
                break;
            case ZX:
                symmetryPoints.add(new Vector2(size - y - 1, size - x - 1));
                break;
            case QUAD:
                if (symmetrySettings.getTeamSymmetry() == Symmetry.Z) {
                    symmetryPoints.add(new Vector2(x, size - y - 1));
                    symmetryPoints.add(new Vector2(size - x - 1, y));
                    symmetryPoints.add(new Vector2(size - x - 1, size - y - 1));
                } else {
                    symmetryPoints.add(new Vector2(size - x - 1, y));
                    symmetryPoints.add(new Vector2(x, size - y - 1));
                    symmetryPoints.add(new Vector2(size - x - 1, size - y - 1));
                }
                break;
            case DIAG:
                if (symmetrySettings.getTeamSymmetry() == Symmetry.ZX) {
                    symmetryPoints.add(new Vector2(size - y - 1, size - x - 1));
                    symmetryPoints.add(new Vector2(y, x));
                    symmetryPoints.add(new Vector2(size - x - 1, size - y - 1));
                } else {
                    symmetryPoints.add(new Vector2(y, x));
                    symmetryPoints.add(new Vector2(size - y - 1, size - x - 1));
                    symmetryPoints.add(new Vector2(size - x - 1, size - y - 1));
                }
                break;
        }
        return symmetryPoints;
    }

    public ArrayList<Float> getSymmetryRotation(float rot) {
        return getSymmetryRotation(rot, SymmetryType.SPAWN);
    }

    private Vector2 getRotatedPoint(float x, float y, float angle) {
        float halfSize = getSize() / 2f;
        float xOffset = x - halfSize;
        float yOffset = y - halfSize;
        double cosAngle = StrictMath.cos(angle);
        double sinAngle = StrictMath.sin(angle);
        float newX = (float) (xOffset * cosAngle - yOffset * sinAngle + halfSize);
        float newY = (float) (xOffset * sinAngle + yOffset * cosAngle + halfSize);
        return new Vector2(newX, newY);
    }

    protected int getMinXBound(SymmetryType symmetryType) {
        Symmetry symmetry = symmetrySettings.getSymmetry(symmetryType);
        switch (symmetry) {
            default:
                return 0;
        }
    }

    public ArrayList<Float> getSymmetryRotation(float rot, SymmetryType symmetryType) {
        ArrayList<Float> symmetryRotation = new ArrayList<>();
        final float xRotation = (float) StrictMath.atan2(-StrictMath.sin(rot), StrictMath.cos(rot));
        final float zRotation = (float) StrictMath.atan2(-StrictMath.cos(rot), StrictMath.sin(rot));
        final float diagRotation = (float) StrictMath.atan2(-StrictMath.cos(rot), -StrictMath.sin(rot));
        Symmetry symmetry = symmetrySettings.getSymmetry(symmetryType);
        Symmetry teamSymmetry = symmetrySettings.getTeamSymmetry();
        switch (symmetry) {
            case POINT2:
            case Z:
            case X:
                symmetryRotation.add(rot + (float) StrictMath.PI);
                break;
            case POINT4:
                symmetryRotation.add(rot + (float) StrictMath.PI);
                symmetryRotation.add(rot + (float) StrictMath.PI / 2);
                symmetryRotation.add(rot - (float) StrictMath.PI / 2);
                break;
            case POINT3:
            case POINT5:
            case POINT6:
            case POINT7:
            case POINT8:
            case POINT9:
            case POINT10:
            case POINT11:
            case POINT12:
            case POINT13:
            case POINT14:
            case POINT15:
            case POINT16:
                int numSymPoints = symmetry.getNumSymPoints();
                for (int i = 1; i < numSymPoints; i++) {
                    symmetryRotation.add(rot + (float) (2 * StrictMath.PI * i / numSymPoints));
                }
                break;
            case XZ:
            case ZX:
                symmetryRotation.add(diagRotation);
                break;
            case QUAD:
                if (teamSymmetry == Symmetry.Z) {
                    symmetryRotation.add(zRotation);
                    symmetryRotation.add(xRotation);
                    symmetryRotation.add(rot + (float) StrictMath.PI);
                } else {
                    symmetryRotation.add(xRotation);
                    symmetryRotation.add(zRotation);
                    symmetryRotation.add(rot + (float) StrictMath.PI);
                }
                break;
            case DIAG:
                if (teamSymmetry == Symmetry.ZX) {
                    symmetryRotation.add(diagRotation);
                    symmetryRotation.add(diagRotation);
                    symmetryRotation.add(rot + (float) StrictMath.PI);
                } else {
                    symmetryRotation.add(diagRotation);
                    symmetryRotation.add(diagRotation);
                    symmetryRotation.add(rot + (float) StrictMath.PI);
                }
                break;
        }
        return symmetryRotation;
    }

    protected int getMinYBound(int x, SymmetryType symmetryType) {
        Symmetry symmetry = symmetrySettings.getSymmetry(symmetryType);
        switch (symmetry) {
            case POINT2:
            case POINT3:
            case POINT4:
            case POINT5:
            case POINT6:
            case POINT7:
            case POINT8:
            case POINT9:
            case POINT10:
            case POINT11:
            case POINT12:
            case POINT13:
            case POINT14:
            case POINT15:
            case POINT16:
                return getMinYFromXOnArc(x, 360f / symmetry.getNumSymPoints());
            case DIAG:
            case XZ:
                return x;
            default:
                return 0;
        }
    }

    protected int getMaxXBound(SymmetryType symmetryType) {
        Symmetry symmetry = symmetrySettings.getSymmetry(symmetryType);
        int size = getSize();
        switch (symmetry) {
            case POINT3:
            case POINT5:
            case POINT6:
            case POINT7:
            case POINT8:
            case POINT9:
            case POINT10:
            case POINT11:
            case POINT12:
            case POINT13:
            case POINT14:
            case POINT15:
            case POINT16:
                return StrictMath.max(getMaxXFromAngle(360f / symmetry.getNumSymPoints()), size / 2 + 1);
            case POINT4:
            case X:
            case QUAD:
            case DIAG:
                return size / 2 + 1;
            default:
                return size;
        }
    }

    protected int getMaxYBound(int x, SymmetryType symmetryType) {
        Symmetry symmetry = symmetrySettings.getSymmetry(symmetryType);
        int size = getSize();
        switch (symmetry) {
            case POINT3:
            case POINT5:
            case POINT6:
            case POINT7:
            case POINT8:
            case POINT9:
            case POINT10:
            case POINT11:
            case POINT12:
            case POINT13:
            case POINT14:
            case POINT15:
            case POINT16:
                return getMaxYFromXOnArc(x, 360f / symmetry.getNumSymPoints());
            case ZX:
            case DIAG:
                return size - x;
            case Z:
            case POINT2:
            case POINT4:
            case QUAD:
                return size / 2 + 1;
            default:
                return size;
        }
    }

    private int getMaxXFromAngle(float angle) {
        int size = getSize();
        int x = (int) StrictMath.round(StrictMath.cos(((angle + 180) / 180) % 2 * StrictMath.PI) * size + size / 2f);
        return StrictMath.max(StrictMath.min(x, size), 0);
    }

    private int getMinYFromXOnArc(int x, float angle) {
        int size = getSize();
        float dx = x - size / 2f;
        int y;
        if (x > getMaxXFromAngle(angle)) {
            y = (int) (size / 2 + StrictMath.tan(((angle + 180) / 180) % 2 * StrictMath.PI) * dx);
        } else {
            y = (int) StrictMath.round(size / 2f - StrictMath.sqrt(size * size - dx * dx));
        }
        return StrictMath.max(StrictMath.min(y, size), 0);
    }

    private int getMaxYFromXOnArc(int x, float angle) {
        int size = getSize();
        float dx = x - size / 2f;
        int y;
        if (x > size / 2) {
            y = (int) (size / 2f + StrictMath.tan(((angle + 180) / 180) % 2 * StrictMath.PI) * dx);
        } else {
            y = size / 2 + 1;
        }
        return StrictMath.max(StrictMath.min(y, getSize()), 0);
    }

    public boolean inTeam(Vector3 pos, boolean reverse) {
        return inTeam(new Vector2(pos), reverse);
    }

    public boolean inTeam(Vector2 pos, boolean reverse) {
        return inTeam((int) pos.getX(), (int) pos.getY(), reverse);
    }

    public boolean inTeam(int x, int y, boolean reverse) {
        return (x >= getMinXBound(SymmetryType.TEAM) && x < getMaxXBound(SymmetryType.TEAM) && y >= getMinYBound(x, SymmetryType.TEAM) && y < getMaxYBound(x, SymmetryType.TEAM)) ^ reverse && inBounds(x, y);
    }

    public boolean inTeamNoBounds(Vector3 pos, boolean reverse) {
        return inTeam(new Vector2(pos), reverse);
    }

    public boolean inTeamNoBounds(Vector2 pos, boolean reverse) {
        return inTeam((int) pos.getX(), (int) pos.getY(), reverse);
    }

    public boolean inTeamNoBounds(int x, int y, boolean reverse) {
        return (x >= getMinXBound(SymmetryType.TEAM) && x < getMaxXBound(SymmetryType.TEAM) && y >= getMinYBound(x, SymmetryType.TEAM) && y < getMaxYBound(x, SymmetryType.TEAM)) ^ reverse;
    }

    public boolean inHalf(Vector3 pos, float angle) {
        return inHalf(new Vector2(pos), angle);
    }

    public boolean inHalf(int x, int y, float angle) {
        return inHalf(new Vector2(x, y), angle);
    }

    public boolean inHalf(Vector2 pos, float angle) {
        float halfSize = getSize() / 2f;
        float vectorAngle = (float) ((new Vector2(halfSize, halfSize).angleTo(pos) * 180f / StrictMath.PI) + 90f + 360f) % 360f;
        float adjustedAngle = (angle + 180f) % 360f;
        if (angle >= 180) {
            return (vectorAngle >= angle || vectorAngle < adjustedAngle) && inBounds(pos);
        } else {
            return (vectorAngle >= angle && vectorAngle < adjustedAngle) && inBounds(pos);
        }
    }

    public boolean inHalfNoBounds(Vector3 pos, float angle) {
        return inHalfNoBounds(new Vector2(pos), angle);
    }

    public boolean inHalfNoBounds(int x, int y, float angle) {
        return inHalfNoBounds(new Vector2(x, y), angle);
    }

    public boolean inHalfNoBounds(Vector2 pos, float angle) {
        float halfSize = getSize() / 2f;
        float vectorAngle = (float) ((new Vector2(halfSize, halfSize).angleTo(pos) * 180f / StrictMath.PI) + 90f + 360f) % 360f;
        float adjustedAngle = (angle + 180f) % 360f;
        if (angle >= 180) {
            return (vectorAngle >= angle || vectorAngle < adjustedAngle);
        } else {
            return (vectorAngle >= angle && vectorAngle < adjustedAngle);
        }
    }

    public void applySymmetry(SymmetryType symmetryType) {
        applySymmetry(symmetryType, false);
    }

    public void applySymmetry(SymmetryType symmetryType, boolean reverse) {
        enqueue(() -> applyWithSymmetry(symmetryType, (x, y) -> {
            if (!reverse) {
                T value = get(x, y);
                applyAtSymmetryPoints(x, y, symmetryType, (sx, sy) -> set(sx, sy, value));
            } else {
                if (symmetrySettings.getSymmetry(symmetryType).getNumSymPoints() != 2) {
                    throw new IllegalArgumentException("Symmetry has more than two symmetry points");
                }
                List<Vector2> symPoints = getSymmetryPoints(x, y, symmetryType);
                symPoints.forEach(symmetryPoint -> set(x, y, get(symmetryPoint)));
            }
        }));
    }

    public void applySymmetry(float angle) {
        enqueue(() -> {
            if (symmetrySettings.getSymmetry(SymmetryType.SPAWN) != Symmetry.POINT2) {
                throw new IllegalArgumentException("Spawn Symmetry must equal POINT2");
            }
            apply((x, y) -> {
                if (inHalf(x, y, angle)) {
                    T value = get(x, y);
                    applyAtSymmetryPoints(x, y, SymmetryType.SPAWN, (sx, sy) -> set(sx, sy, value));
                }
            });
        });
    }

    protected U enlarge(int size) {
        return enlarge(size, SymmetryType.SPAWN);
    }

    protected U shrink(int size) {
        return shrink(size, SymmetryType.SPAWN);
    }

    protected U enlarge(int newSize, SymmetryType symmetryType) {
        T[][] smallMask = mask;
        int oldSize = getSize();
        float scale = (float) newSize / oldSize;
        mask = getEmptyMask(newSize);
        setWithSymmetry(symmetryType, (x, y) -> {
            int smallX = StrictMath.min((int) (x / scale), oldSize - 1);
            int smallY = StrictMath.min((int) (y / scale), oldSize - 1);
            return smallMask[smallX][smallY];
        });
        return (U) this;
    }

    protected U shrink(int newSize, SymmetryType symmetryType) {
        T[][] largeMask = mask;
        int oldSize = getSize();
        float scale = (float) oldSize / newSize;
        mask = getEmptyMask(newSize);
        setWithSymmetry(symmetryType, (x, y) -> {
            int largeX = StrictMath.min(StrictMath.round(x * scale + scale / 2), oldSize - 1);
            int largeY = StrictMath.min(StrictMath.round(y * scale + scale / 2), oldSize - 1);
            return largeMask[largeX][largeY];
        });
        return (U) this;
    }

    private U interpolate(int newSize) {
        return interpolate(newSize, SymmetryType.SPAWN);
    }

    private U interpolate(int newSize, SymmetryType symmetryType) {
        int oldSize = getSize();
        enlarge(newSize, symmetryType);
        blur(StrictMath.round((float) newSize / oldSize / 2 - 1));
        return (U) this;
    }

    private U decimate(int newSize) {
        return decimate(newSize, SymmetryType.SPAWN);
    }

    private U decimate(int newSize, SymmetryType symmetryType) {
        int oldSize = getSize();
        blur(StrictMath.round((float) oldSize / newSize / 2 - 1));
        shrink(newSize, symmetryType);
        return (U) this;
    }

    public U flip(SymmetryType symmetryType) {
        enqueue(() -> {
            Symmetry symmetry = symmetrySettings.getSymmetry(symmetryType);
            if (symmetry.getNumSymPoints() != 2) {
                throw new IllegalArgumentException("Cannot flip non single axis symmetry");
            }
            int size = getSize();
            T[][] newMask = getEmptyMask(size);
            apply((x, y) -> {
                List<Vector2> symmetryPoints = getSymmetryPoints(x, y, symmetryType);
                newMask[x][y] = get(symmetryPoints.get(0));
            });
            this.mask = newMask;
        });
        return (U) this;
    }

    public void set(BiFunction<Integer, Integer, T> valueFunction) {
        apply((x, y) -> set(x, y, valueFunction.apply(x, y)));
    }

    public void setWithSymmetry(SymmetryType symmetryType, BiFunction<Integer, Integer, T> valueFunction) {
        applyWithSymmetry(symmetryType, (x, y) -> {
            T value = valueFunction.apply(x, y);
            applyAtSymmetryPoints(x, y, symmetryType, (sx, sy) -> set(sx, sy, value));
        });
    }

    public void apply(BiConsumer<Integer, Integer> maskAction) {
        int size = getSize();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                maskAction.accept(x, y);
            }
        }
    }

    public void applyWithSymmetry(SymmetryType symmetryType, BiConsumer<Integer, Integer> maskAction) {
        int minX = getMinXBound(symmetryType);
        int maxX = getMaxXBound(symmetryType);
        for (int x = minX; x < maxX; x++) {
            int minY = getMinYBound(x, symmetryType);
            int maxY = getMaxYBound(x, symmetryType);
            for (int y = minY; y < maxY; y++) {
                maskAction.accept(x, y);
            }
        }
    }

    public void applyAtSymmetryPoints(int x, int y, SymmetryType symmetryType, BiConsumer<Integer, Integer> action) {
        action.accept(x, y);
        List<Vector2> symPoints = getSymmetryPoints(x, y, symmetryType);
        symPoints.forEach(symPoint -> action.accept((int) symPoint.getX(), (int) symPoint.getY()));
    }

    public void applyWithOffset(U other, TriConsumer<Integer, Integer, T> action, int xCoordinate, int yCoordinate, boolean center, boolean wrapEdges) {
        int size = getSize();
        int otherSize = other.getSize();
        int smallerSize = StrictMath.min(size, otherSize);
        int offsetX;
        int offsetY;
        if (center) {
            offsetX = xCoordinate - smallerSize / 2;
            offsetY = yCoordinate - smallerSize / 2;
        } else {
            offsetX = xCoordinate;
            offsetY = yCoordinate;
        }
        if (size >= otherSize) {
            other.apply((x, y) -> {
                int shiftX = getShiftedValue(x, offsetX, size, wrapEdges);
                int shiftY = getShiftedValue(y, offsetY, size, wrapEdges);
                if (inBounds(shiftX, shiftY)) {
                    T value = other.get(x, y);
                    applyAtSymmetryPoints(shiftX, shiftY, SymmetryType.SPAWN, (sx, sy) -> action.accept(sx, sy, value));
                }
            });
        } else {
            apply((x, y) -> {
                int shiftX = getShiftedValue(x, offsetX, otherSize, wrapEdges);
                int shiftY = getShiftedValue(y, offsetY, otherSize, wrapEdges);
                if (other.inBounds(shiftX, shiftY)) {
                    T value = other.get(shiftX, shiftY);
                    action.accept(x, y, value);
                }
            });
        }
    }

    protected void enqueue(Runnable function) {
        enqueue((ignored) -> function.run());
    }

    protected void enqueue(Consumer<List<Mask<?, ?>>> function, Mask<?, ?>... usedMasks) {
        enqueue((U) this, function, usedMasks);
    }

    protected <V extends Mask<?, ?>> void enqueue(V resultMask, Consumer<List<Mask<?, ?>>> function, Mask<?, ?>... usedMasks) {
        List<Mask<?, ?>> dependencies = Arrays.asList(usedMasks);
        if (parallel && !Pipeline.isStarted()) {
            if (dependencies.stream().anyMatch(dep -> !dep.parallel)) {
                throw new IllegalArgumentException("Non parallel masks used as dependents");
            }
            Pipeline.add(this, resultMask, dependencies, function);
        } else {
            function.accept(dependencies);
            String callingMethod = Util.getStackTraceMethodInPackage("com.faforever.neroxis.map", "enqueue");
            String callingLine = Util.getStackTraceLineInPackage("com.faforever.neroxis.map.");
            VisualDebugger.visualizeMask(resultMask, callingMethod, callingLine);
        }
    }

    protected void assertCompatibleMask(Mask<?, ?> other) {
        int otherSize = other.getSize();
        int size = getSize();
        String name = getName();
        String otherName = other.getName();
        if (otherSize != size) {
            throw new IllegalArgumentException(String.format("Masks not the same size: %s is %d and %s is %d", name, size, otherName, otherSize));
        }
        SymmetrySettings symmetrySettings = getSymmetrySettings();
        SymmetrySettings otherSymmetrySettings = other.getSymmetrySettings();
        if (!symmetrySettings.equals(otherSymmetrySettings)) {
            throw new IllegalArgumentException(String.format("Masks not the same symmetry: %s is %s and %s is %s", name, symmetrySettings, otherName, otherSymmetrySettings));
        }
        if (isParallel() && !Pipeline.isStarted() && !other.isParallel()) {
            throw new IllegalArgumentException(String.format("Masks not the same processing chain: %s and %s", name, otherName));
        }
    }

    protected void assertSmallerSize(int size) {
        int actualSize = getSize();
        if (size > actualSize) {
            throw new IllegalArgumentException("Intended mask size is larger than base mask size: Mask is " + actualSize + " and size is " + size);
        }
    }

    protected void assertSize(int size) {
        int actualSize = getSize();
        if (size != actualSize) {
            throw new IllegalArgumentException("Mask size is incorrect: Mask is " + actualSize + " and size is " + size);
        }
    }

    protected void assertNotPipelined() {
        if (parallel && !Pipeline.isStarted()) {
            throw new IllegalStateException("Mask is pipelined and cannot return an immediate result");
        }
    }

    public U getFinalMask() {
        Pipeline.await(this);
        return copy();
    }

    public U startVisualDebugger() {
        return startVisualDebugger(name == null ? toString() : name);
    }

    public U startVisualDebugger(String maskName) {
        visualName = maskName;
        visualDebug = true;
        VisualDebugger.createGUI();
        show();
        return (U) this;
    }

    public void show() {
        if (!parallel) {
            VisualDebugger.visualizeMask(this, "show");
        }
    }

    public U fillSides(int extent, T value) {
        return fillSides(extent, value, SymmetryType.TEAM);
    }

    public U fillSides(int extent, T value, SymmetryType symmetryType) {
        enqueue(() -> {
            int size = getSize();
            switch (symmetrySettings.getSymmetry(symmetryType)) {
                case Z:
                    fillRect(0, 0, extent / 2, size, value).fillRect(size - extent / 2, 0, size - extent / 2, size, value);
                    break;
                case X:
                    fillRect(0, 0, size, extent / 2, value).fillRect(0, size - extent / 2, size, extent / 2, value);
                    break;
                case XZ:
                    fillParallelogram(0, 0, size, extent * 3 / 4, 0, -1, value).fillParallelogram(size - extent * 3 / 4, size, size, extent * 3 / 4, 0, -1, value);
                    break;
                case ZX:
                    fillParallelogram(size - extent * 3 / 4, 0, extent * 3 / 4, extent * 3 / 4, 1, 0, value).fillParallelogram(-extent * 3 / 4, size - extent * 3 / 4, extent * 3 / 4, extent * 3 / 4, 1, 0, value);
                    break;
            }
            applySymmetry(symmetryType);
        });
        return (U) this;
    }

    public U fillCenter(int extent, T value) {
        return fillCenter(extent, value, SymmetryType.SPAWN);
    }

    public U fillCenter(int extent, T value, SymmetryType symmetryType) {
        enqueue(() -> {
            int size = getSize();
            switch (symmetrySettings.getSymmetry(symmetryType)) {
                case POINT2:
                case POINT3:
                case POINT4:
                case POINT5:
                case POINT6:
                case POINT7:
                case POINT8:
                case POINT9:
                case POINT10:
                case POINT11:
                case POINT12:
                case POINT13:
                case POINT14:
                case POINT15:
                case POINT16:
                    fillCircle((float) size / 2, (float) size / 2, extent * 3 / 4f, value);
                    break;
                case Z:
                    fillRect(0, size / 2 - extent / 2, size, extent, value);
                    break;
                case X:
                    fillRect(size / 2 - extent / 2, 0, extent, size, value);
                    break;
                case XZ:
                    fillDiagonal(extent * 3 / 4, false, value);
                    break;
                case ZX:
                    fillDiagonal(extent * 3 / 4, true, value);
                    break;
                case DIAG:
                    if (symmetrySettings.getTeamSymmetry() == Symmetry.DIAG) {
                        fillDiagonal(extent * 3 / 8, false, value);
                        fillDiagonal(extent * 3 / 8, true, value);
                    } else {
                        fillDiagonal(extent * 3 / 16, false, value);
                        fillDiagonal(extent * 3 / 16, true, value);
                        fillCenter(extent, value, SymmetryType.TEAM);
                    }
                    break;
                case QUAD:
                    if (symmetrySettings.getTeamSymmetry() == Symmetry.QUAD) {
                        fillRect(size / 2 - extent / 4, 0, extent / 2, size, value);
                        fillRect(0, size / 2 - extent / 4, size, extent / 2, value);
                    } else {
                        fillRect(size / 2 - extent / 8, 0, extent / 4, size, value);
                        fillRect(0, size / 2 - extent / 8, size, extent / 4, value);
                        fillCenter(extent, value, SymmetryType.TEAM);
                    }
                    break;
            }
            applySymmetry(SymmetryType.SPAWN);
        });
        return (U) this;
    }

    public U fillCircle(Vector3 v, float radius, T value) {
        return fillCircle(new Vector2(v), radius, value);
    }

    public U fillCircle(Vector2 v, float radius, T value) {
        return fillCircle(v.getX(), v.getY(), radius, value);
    }

    public U fillCircle(float x, float y, float radius, T value) {
        return fillArc(x, y, 0, 360, radius, value);
    }

    public U fillArc(float x, float y, float startAngle, float endAngle, float radius, T value) {
        enqueue(() -> {
            float dx;
            float dy;
            float radius2 = (radius + .5f) * (radius + .5f);
            float radiansToDegreeFactor = (float) (180 / StrictMath.PI);
            for (int cx = StrictMath.round(x - radius); cx < StrictMath.round(x + radius + 1); cx++) {
                for (int cy = StrictMath.round(y - radius); cy < StrictMath.round(y + radius + 1); cy++) {
                    dx = x - cx;
                    dy = y - cy;
                    float angle = (float) (StrictMath.atan2(dy, dx) / radiansToDegreeFactor + 360) % 360;
                    if (inBounds(cx, cy) && dx * dx + dy * dy <= radius2 && angle >= startAngle && angle <= endAngle) {
                        set(cx, cy, value);
                    }
                }
            }
        });
        return (U) this;
    }

    public U fillSquare(Vector2 v, int extent, T value) {
        return fillSquare((int) v.getX(), (int) v.getY(), extent, value);
    }

    public U fillSquare(int x, int y, int extent, T value) {
        return fillRect(x, y, extent, extent, value);
    }

    public U fillRect(Vector2 v, int width, int height, T value) {
        return fillRect((int) v.getX(), (int) v.getY(), width, height, value);
    }

    public U fillRect(int x, int y, int width, int height, T value) {
        return fillParallelogram(x, y, width, height, 0, 0, value);
    }

    public U fillRectFromPoints(int x1, int x2, int z1, int z2, T value) {
        int smallX = StrictMath.min(x1, x2);
        int bigX = StrictMath.max(x1, x2);
        int smallZ = StrictMath.min(z1, z2);
        int bigZ = StrictMath.max(z1, z2);
        return fillRect(smallX, smallZ, bigX - smallX, bigZ - smallZ, value);
    }

    public U fillParallelogram(Vector2 v, int width, int height, int xSlope, int ySlope, T value) {
        return fillParallelogram((int) v.getX(), (int) v.getY(), width, height, xSlope, ySlope, value);
    }

    public U fillParallelogram(int x, int y, int width, int height, int xSlope, int ySlope, T value) {
        enqueue(() -> {
            for (int px = 0; px < width; px++) {
                for (int py = 0; py < height; py++) {
                    int calcX = x + px + py * xSlope;
                    int calcY = y + py + px * ySlope;
                    if (inBounds(calcX, calcY)) {
                        set(calcX, calcY, value);
                    }
                }
            }
        });
        return (U) this;
    }

    public U fillDiagonal(int extent, boolean inverted, T value) {
        enqueue(() -> {
            int size = getSize();
            for (int cx = -extent; cx < extent; cx++) {
                for (int y = 0; y < size; y++) {
                    int x;
                    if (inverted) {
                        x = size - (cx + y);
                    } else {
                        x = cx + y;
                    }
                    if (x >= 0 && x < size) {
                        set(x, y, value);
                    }
                }
            }
        });
        return (U) this;
    }

    public U fillEdge(int rimWidth, T value) {
        enqueue(() -> {
            int size = getSize();
            for (int a = 0; a < rimWidth; a++) {
                for (int b = 0; b < size - rimWidth; b++) {
                    set(a, b, value);
                    set(size - 1 - a, size - 1 - b, value);
                    set(b, size - 1 - a, value);
                    set(size - 1 - b, a, value);
                }
            }
        });
        return (U) this;
    }

    public U fillCoordinates(Collection<Vector2> coordinates, T value) {
        enqueue(() -> coordinates.forEach(location -> set(location, value)));
        return (U) this;
    }

    protected void maskFill(T value) {
        maskFill(mask, value);
    }

    protected void maskFill(T[][] mask, T value) {
        for (int r = 0; r < mask.length; ++r) {
            int len = mask[r].length;

            if (len > 0) {
                mask[r][0] = value;
            }

            //Value of i will be [1, 2, 4, 8, 16, 32, ..., len]
            for (int i = 1; i < len; i += i) {
                System.arraycopy(mask[r], 0, mask[r], i, StrictMath.min((len - i), i));
            }
        }
    }

    @Override
    public String toString() {
        if (name != null) {
            return String.format("Mask(name=%s,size=%d)", name, getSize());
        } else {
            return getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
        }
    }
}
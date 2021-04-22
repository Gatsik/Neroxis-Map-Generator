package neroxis.map;

import neroxis.util.Vector2f;
import neroxis.util.VisualDebugger;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;

@SuppressWarnings("unchecked")
public strictfp abstract class NumberMask<T extends Number & Comparable<T>, U extends NumberMask<T, U>> extends Mask<T, U> {

    protected NumberMask(Long seed, SymmetrySettings symmetrySettings, String name) {
        super(seed, symmetrySettings, name);
    }

    protected NumberMask(Long seed, SymmetrySettings symmetrySettings, String name, boolean parallel) {
        super(seed, symmetrySettings, name, parallel);
    }

    @Override
    public abstract U copy();

    public abstract T getDefaultValue();

    public abstract T add(T val1, T val2);

    public abstract T subtract(T val1, T val2);

    public abstract T multiply(T val1, T val2);

    public abstract T divide(T val1, T val2);

    public abstract T getAvg();

    public abstract U blur(int radius, BooleanMask limiter);

    public void addValueAt(Vector2f loc, T value) {
        addValueAt((int) loc.getX(), (int) loc.getY(), value);
    }

    public void addValueAt(int x, int y, T value) {
        setValueAt(x, y, add(getValueAt(x, y), value));
    }

    public void subtractValueAt(Vector2f loc, T value) {
        subtractValueAt((int) loc.getX(), (int) loc.getY(), value);
    }

    public void subtractValueAt(int x, int y, T value) {
        setValueAt(x, y, subtract(getValueAt(x, y), value));
    }

    public void multiplyValueAt(Vector2f loc, T value) {
        multiplyValueAt((int) loc.getX(), (int) loc.getY(), value);
    }

    public void multiplyValueAt(int x, int y, T value) {
        setValueAt(x, y, multiply(getValueAt(x, y), value));
    }

    public boolean valueAtEqualTo(int x, int y, T value) {
        return getValueAt(x, y).compareTo(value) == 0;
    }

    public boolean valueAtLessThan(int x, int y, T value) {
        return getValueAt(x, y).compareTo(value) < 0;
    }

    public boolean valueAtLessThanEqualTo(int x, int y, T value) {
        return getValueAt(x, y).compareTo(value) <= 0;
    }

    public boolean valueAtGreaterThan(int x, int y, T value) {
        return getValueAt(x, y).compareTo(value) > 0;
    }

    public boolean valueAtGreaterThanEqualTo(int x, int y, T value) {
        return getValueAt(x, y).compareTo(value) >= 0;
    }

    public boolean isLocalMax(int x, int y) {
        T value = getValueAt(x, y);
        return ((x > 0 && valueAtLessThanEqualTo(x - 1, y, value))
                && (x < getSize() - 1 && valueAtLessThanEqualTo(x + 1, y, value))
                && (y > 0 && valueAtLessThanEqualTo(x, y - 1, value))
                && (y < getSize() - 1 && valueAtLessThanEqualTo(x, y + 1, value))
                && valueAtLessThanEqualTo(x - 1, y - 1, value)
                && valueAtLessThanEqualTo(x + 1, y - 1, value)
                && valueAtLessThanEqualTo(x - 1, y + 1, value)
                && valueAtLessThanEqualTo(x + 1, y + 1, value));
    }

    public boolean isLocal1DMax(int x, int y) {
        T value = getValueAt(x, y);
        return (((x > 0 && valueAtLessThanEqualTo(x - 1, y, value))
                && (x < getSize() - 1 && valueAtLessThanEqualTo(x + 1, y, value)))
                || ((y > 0 && valueAtLessThanEqualTo(x, y - 1, value))
                && (y < getSize() - 1 && valueAtLessThanEqualTo(x, y + 1, value))));
    }

    public T getMin() {
        return Arrays.stream(mask).flatMap(Arrays::stream).min(Comparator.comparing(value -> value)).orElseThrow(() -> new IllegalStateException("Empty Mask"));
    }

    public T getMax() {
        return Arrays.stream(mask).flatMap(Arrays::stream).max(Comparator.comparing(value -> value)).orElseThrow(() -> new IllegalStateException("Empty Mask"));
    }

    public T getSum() {
        return Arrays.stream(mask).flatMap(Arrays::stream).reduce(this::add).orElseThrow(() -> new IllegalStateException("Empty Mask"));
    }

    public U clear() {
        execute(() -> {
            modify((x, y) -> getDefaultValue());
            VisualDebugger.visualizeMask(this);
        });
        return (U) this;
    }

    public U init(BooleanMask other, T low, T high) {
        plannedSize = other.getSize();
        execute(() -> {
            setSize(other.getSize());
            assertCompatibleMask(other);
            modify((x, y) -> other.getValueAt(x, y) ? high : low);
            VisualDebugger.visualizeMask(this);
        }, other);
        return (U) this;
    }

    public U init(U other) {
        plannedSize = other.getSize();
        execute(() -> {
            setSize(other.getSize());
            assertCompatibleMask(other);
            modify(other::getValueAt);
            VisualDebugger.visualizeMask(this);
        }, other);
        return (U) this;
    }

    public U add(U other) {
        execute(() -> {
            assertCompatibleMask(other);
            add(other::getValueAt);
            VisualDebugger.visualizeMask(this);
        }, other);
        return (U) this;
    }

    public U add(BooleanMask other, T value) {
        execute(() -> {
            assertCompatibleMask(other);
            add((x, y) -> other.getValueAt(x, y) ? value : getDefaultValue());
            VisualDebugger.visualizeMask(this);
        }, other);
        return (U) this;
    }

    public U add(T val) {
        execute(() -> {
            add((x, y) -> val);
            VisualDebugger.visualizeMask(this);
        });
        return (U) this;
    }

    public U addWeighted(U other, T weight) {
        execute(() -> {
            assertCompatibleMask(other);
            add((x, y) -> multiply(other.getValueAt(x, y), weight));
            VisualDebugger.visualizeMask(this);
        }, other);
        return (U) this;
    }

    public U addWithOffset(U other, Vector2f loc, boolean centered, boolean wrapEdges) {
        return addWithOffset(other, (int) loc.getX(), (int) loc.getY(), centered, wrapEdges);
    }

    public U addWithOffset(U other, int xCoordinate, int yCoordinate, boolean center, boolean wrapEdges) {
        execute(() -> {
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
                        T value = other.getValueAt(x, y);
                        addValueAt(shiftX, shiftY, value);
                        List<Vector2f> symmetryPoints = getSymmetryPoints(shiftX, shiftY, SymmetryType.SPAWN);
                        for (Vector2f symmetryPoint : symmetryPoints) {
                            addValueAt(symmetryPoint, value);
                        }
                    }
                });
            } else {
                apply((x, y) -> {
                    int shiftX = getShiftedValue(x, offsetX, otherSize, wrapEdges);
                    int shiftY = getShiftedValue(y, offsetY, otherSize, wrapEdges);
                    if (other.inBounds(shiftX, shiftY)) {
                        addValueAt(x, y, other.getValueAt(shiftX, shiftY));
                    }
                });
            }
        }, other);
        return (U) this;
    }

    public U subtractAvg() {
        execute(() -> subtract(getAvg()));
        return (U) this;
    }

    public U subtract(T val) {
        execute(() -> subtract((x, y) -> val));
        return (U) this;
    }

    public U subtract(U other) {
        execute(() -> {
            assertCompatibleMask(other);
            subtract(other::getValueAt);
            VisualDebugger.visualizeMask(this);
        }, other);
        return (U) this;
    }

    public U subtract(BooleanMask other, T value) {
        execute(() -> {
            assertCompatibleMask(other);
            subtract((x, y) -> other.getValueAt(x, y) ? value : getDefaultValue());
            VisualDebugger.visualizeMask(this);
        }, other);
        return (U) this;
    }

    public U multiply(U other) {
        execute(() -> {
            assertCompatibleMask(other);
            multiply(other::getValueAt);
            VisualDebugger.visualizeMask(this);
        });
        return (U) this;
    }

    public U multiply(T val) {
        execute(() -> {
            multiply((x, y) -> val);
            VisualDebugger.visualizeMask(this);
        });
        return (U) this;
    }

    private U multiplyWithOffset(U other, Vector2f loc, boolean centered, boolean wrapEdges) {
        return multiplyWithOffset(other, (int) loc.getX(), (int) loc.getY(), centered, wrapEdges);
    }

    private U multiplyWithOffset(U other, int xCoordinate, int yCoordinate, boolean center, boolean wrapEdges) {
        execute(() -> {
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
                        T value = other.getValueAt(x, y);
                        multiplyValueAt(shiftX, shiftY, value);
                        List<Vector2f> symmetryPoints = getSymmetryPoints(shiftX, shiftY, SymmetryType.SPAWN);
                        for (Vector2f symmetryPoint : symmetryPoints) {
                            multiplyValueAt(symmetryPoint, value);
                        }
                    }
                });
            } else {
                apply((x, y) -> {
                    int shiftX = getShiftedValue(x, offsetX, otherSize, wrapEdges);
                    int shiftY = getShiftedValue(y, offsetY, otherSize, wrapEdges);
                    if (other.inBounds(shiftX, shiftY)) {
                        multiplyValueAt(x, y, other.getValueAt(shiftX, shiftY));
                    }
                });
            }
        }, other);
        return (U) this;
    }

    public U max(U other) {
        execute(() -> {
            assertCompatibleMask(other);
            modify((x, y) -> {
                T thisVal = getValueAt(x, y);
                T otherVal = other.getValueAt(x, y);
                return thisVal.compareTo(otherVal) > 0 ? thisVal : otherVal;
            });
            VisualDebugger.visualizeMask(this);
        }, other);
        return (U) this;
    }

    public U clampMax(BooleanMask area, T val) {
        execute(() -> {
            assertCompatibleMask(area);
            modify((x, y) -> {
                T thisVal = getValueAt(x, y);
                return area.getValueAt(x, y) ? (thisVal.compareTo(val) < 0 ? val : thisVal) : thisVal;
            });
            VisualDebugger.visualizeMask(this);
        }, area);
        return (U) this;
    }

    public U clampMax(T val) {
        execute(() -> {
            modify((x, y) -> {
                T thisVal = getValueAt(x, y);
                return thisVal.compareTo(val) < 0 ? thisVal : val;
            });
            VisualDebugger.visualizeMask(this);
        });
        return (U) this;
    }

    public U min(U other) {
        execute(() -> {
            assertCompatibleMask(other);
            modify((x, y) -> {
                T thisVal = getValueAt(x, y);
                T otherVal = other.getValueAt(x, y);
                return thisVal.compareTo(otherVal) < 0 ? thisVal : otherVal;
            });
            VisualDebugger.visualizeMask(this);
        }, other);
        return (U) this;
    }

    public U clampMin(BooleanMask area, T val) {
        execute(() -> {
            assertCompatibleMask(area);
            modify((x, y) -> {
                T thisVal = getValueAt(x, y);
                return area.getValueAt(x, y) ? (thisVal.compareTo(val) > 0 ? val : thisVal) : thisVal;
            });
            VisualDebugger.visualizeMask(this);
        }, area);
        return (U) this;
    }

    public U clampMin(T val) {
        execute(() -> {
            modify((x, y) -> {
                T thisVal = getValueAt(x, y);
                return thisVal.compareTo(val) > 0 ? thisVal : val;
            });
            VisualDebugger.visualizeMask(this);
        });
        return (U) this;
    }

    public U threshold(T val) {
        execute(() -> {
            modify((x, y) -> {
                T thisVal = getValueAt(x, y);
                return thisVal.compareTo(val) > 0 ? getDefaultValue() : getValueAt(x, y);
            });
            VisualDebugger.visualizeMask(this);
        });
        return (U) this;
    }

    public U interpolate() {
        return blur(1);
    }

    public U zeroOutsideRange(T min, T max) {
        execute(() -> {
            modify((x, y) -> valueAtLessThan(x, y, min) || valueAtGreaterThan(x, y, max) ? getDefaultValue() : getValueAt(x, y));
            VisualDebugger.visualizeMask(this);
        });
        return (U) this;
    }

    public U setToValue(BooleanMask other, T val) {
        execute(() -> {
            assertCompatibleMask(other);
            modify((x, y) -> other.getValueAt(x, y) ? val : getValueAt(x, y));
            VisualDebugger.visualizeMask(this);
        }, other);
        return (U) this;
    }

    public U replaceValues(BooleanMask other, U replacement) {
        execute(() -> {
            assertCompatibleMask(other);
            assertCompatibleMask(replacement);
            modify((x, y) -> other.getValueAt(x, y) ? replacement.getValueAt(x, y) : getValueAt(x, y));
            VisualDebugger.visualizeMask(this);
        }, other, replacement);
        return (U) this;
    }

    public U zeroInRange(T min, T max) {
        execute(() -> {
            modify((x, y) -> valueAtGreaterThanEqualTo(x, y, min) && valueAtLessThan(x, y, max) ? getDefaultValue() : getValueAt(x, y));
            VisualDebugger.visualizeMask(this);
        });
        return (U) this;
    }

    public BooleanMask convertToBooleanMask(T minValue, T maxValue) {
        Long seed = random != null ? random.nextLong() : null;
        BooleanMask newMask = new BooleanMask(this, minValue, maxValue, seed);
        VisualDebugger.visualizeMask(this);
        return newMask;
    }

    public BooleanMask getLocalMaximums(T minValue, T maxValue) {
        BooleanMask localMaxima = new BooleanMask(getSize(), random.nextLong(), symmetrySettings);
        applyWithSymmetry(SymmetryType.SPAWN, (x, y) -> {
            T value = getValueAt(x, y);
            if (value.compareTo(minValue) >= 0 && value.compareTo(maxValue) < 0 && isLocalMax(x, y)) {
                localMaxima.setValueAt(x, y, true);
                List<Vector2f> symmetryPoints = getSymmetryPoints(x, y, SymmetryType.SPAWN);
                symmetryPoints.forEach(symmetryPoint -> localMaxima.setValueAt(symmetryPoint, true));
            }
        });
        return localMaxima;
    }

    public BooleanMask getLocal1DMaximums(T minValue, T maxValue) {
        BooleanMask localMaxima = new BooleanMask(getSize(), random.nextLong(), symmetrySettings);
        applyWithSymmetry(SymmetryType.SPAWN, (x, y) -> {
            T value = getValueAt(x, y);
            if (value.compareTo(minValue) >= 0 && value.compareTo(maxValue) < 0 && isLocalMax(x, y)) {
                localMaxima.setValueAt(x, y, true);
                List<Vector2f> symmetryPoints = getSymmetryPoints(x, y, SymmetryType.SPAWN);
                symmetryPoints.forEach(symmetryPoint -> localMaxima.setValueAt(symmetryPoint, true));
            }
        });
        return localMaxima;
    }

    public FloatMask getDistanceFieldForRange(T minValue, T maxValue) {
        return convertToBooleanMask(minValue, maxValue).getDistanceField();
    }

    protected void add(BiFunction<Integer, Integer, T> valueFunction) {
        int size = getSize();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                addValueAt(x, y, valueFunction.apply(x, y));
            }
        }
    }

    protected void addWithSymmetry(SymmetryType symmetryType, BiFunction<Integer, Integer, T> valueFunction) {
        int minX = getMinXBound(symmetryType);
        int maxX = getMaxXBound(symmetryType);
        for (int x = minX; x < maxX; x++) {
            int minY = getMinYBound(x, symmetryType);
            int maxY = getMaxYBound(x, symmetryType);
            for (int y = minY; y < maxY; y++) {
                T value = valueFunction.apply(x, y);
                addValueAt(x, y, value);
                Vector2f location = new Vector2f(x, y);
                List<Vector2f> symmetryPoints = getSymmetryPoints(location, symmetryType);
                symmetryPoints.forEach(symmetryPoint -> addValueAt(symmetryPoint, value));
            }
        }
    }

    protected void subtract(BiFunction<Integer, Integer, T> valueFunction) {
        int size = getSize();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                subtractValueAt(x, y, valueFunction.apply(x, y));
            }
        }
    }

    protected void subtractWithSymmetry(SymmetryType symmetryType, BiFunction<Integer, Integer, T> valueFunction) {
        int minX = getMinXBound(symmetryType);
        int maxX = getMaxXBound(symmetryType);
        for (int x = minX; x < maxX; x++) {
            int minY = getMinYBound(x, symmetryType);
            int maxY = getMaxYBound(x, symmetryType);
            for (int y = minY; y < maxY; y++) {
                T value = valueFunction.apply(x, y);
                subtractValueAt(x, y, value);
                Vector2f location = new Vector2f(x, y);
                List<Vector2f> symmetryPoints = getSymmetryPoints(location, symmetryType);
                symmetryPoints.forEach(symmetryPoint -> subtractValueAt(symmetryPoint, value));
            }
        }
    }

    protected void multiply(BiFunction<Integer, Integer, T> valueFunction) {
        int size = getSize();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                multiplyValueAt(x, y, valueFunction.apply(x, y));
            }
        }
    }

    protected void multiplyWithSymmetry(SymmetryType symmetryType, BiFunction<Integer, Integer, T> valueFunction) {
        int minX = getMinXBound(symmetryType);
        int maxX = getMaxXBound(symmetryType);
        for (int x = minX; x < maxX; x++) {
            int minY = getMinYBound(x, symmetryType);
            int maxY = getMaxYBound(x, symmetryType);
            for (int y = minY; y < maxY; y++) {
                T value = valueFunction.apply(x, y);
                multiplyValueAt(x, y, value);
                Vector2f location = new Vector2f(x, y);
                List<Vector2f> symmetryPoints = getSymmetryPoints(location, symmetryType);
                symmetryPoints.forEach(symmetryPoint -> multiplyValueAt(symmetryPoint, value));
            }
        }
    }
}

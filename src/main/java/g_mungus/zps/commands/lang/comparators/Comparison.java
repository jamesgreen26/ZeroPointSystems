package g_mungus.zps.commands.lang.comparators;

@FunctionalInterface
public interface Comparison<T> {
    boolean test(T left, T right);
}

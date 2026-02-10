package g_mungus.zps.commands.lang.v2.comparators;

public interface ScriptComparator<T> {
    String getName();

    boolean compare(T left, T right);
}

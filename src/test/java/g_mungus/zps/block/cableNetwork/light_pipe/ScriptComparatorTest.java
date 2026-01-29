package g_mungus.zps.block.cableNetwork.light_pipe;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScriptComparatorTest {

    // Test cases for EQUALS mode (exact matching with wildcards)

    @Test
    void equals_exactMatchWithoutWildcards() {
        assertTrue(ScriptComparator.ComparisonMode.equals.compare("hello", "hello"));
    }

    @Test
    void equals_noMatchDifferentStrings() {
        assertFalse(ScriptComparator.ComparisonMode.equals.compare("hello", "world"));
    }

    @Test
    void equals_differentLengthsNoMatch() {
        assertFalse(ScriptComparator.ComparisonMode.equals.compare("hello", "hi"));
    }

    @Test
    void equals_wildcardInFirstString() {
        assertTrue(ScriptComparator.ComparisonMode.equals.compare("h*llo", "hello"));
    }

    @Test
    void equals_wildcardInSecondString() {
        assertTrue(ScriptComparator.ComparisonMode.equals.compare("hello", "h*llo"));
    }

    @Test
    void equals_wildcardsInBothStrings() {
        assertTrue(ScriptComparator.ComparisonMode.equals.compare("h*ll*", "*ello"));
    }

    @Test
    void equals_allWildcardsMatchAnyString() {
        assertTrue(ScriptComparator.ComparisonMode.equals.compare("***", "car"));
    }

    @Test
    void equals_allWildcardsDifferentLength() {
        assertFalse(ScriptComparator.ComparisonMode.equals.compare("***", "a"));
    }

    @Test
    void equals_wildcardMatchesAnyCharacter() {
        assertTrue(ScriptComparator.ComparisonMode.equals.compare("h*llo", "hallo"));
    }

    @Test
    void equals_emptyStrings() {
        assertTrue(ScriptComparator.ComparisonMode.equals.compare("", ""));
    }

    @Test
    void equals_singleWildcard() {
        assertTrue(ScriptComparator.ComparisonMode.equals.compare("*", "a"));
    }

    @Test
    void equals_multipleWildcardsInRow() {
        assertTrue(ScriptComparator.ComparisonMode.equals.compare("***", "abc"));
    }

    // Test cases for CONTAINS mode (substring matching with wildcards)

    @Test
    void contains_simpleSubstringWithoutWildcards() {
        assertTrue(ScriptComparator.ComparisonMode.contains.compare("hello world", "world"));
    }

    @Test
    void contains_substringNotFound() {
        assertFalse(ScriptComparator.ComparisonMode.contains.compare("hello", "xyz"));
    }

    @Test
    void contains_patternAtStart() {
        assertTrue(ScriptComparator.ComparisonMode.contains.compare("hello world", "hello"));
    }

    @Test
    void contains_patternAtEnd() {
        assertTrue(ScriptComparator.ComparisonMode.contains.compare("hello world", "world"));
    }

    @Test
    void contains_patternInMiddle() {
        assertTrue(ScriptComparator.ComparisonMode.contains.compare("hello world", "lo wo"));
    }

    @Test
    void contains_wildcardPattern() {
        assertTrue(ScriptComparator.ComparisonMode.contains.compare("12345", "*23**"));
    }

    @Test
    void contains_wildcardPatternTooLong() {
        assertFalse(ScriptComparator.ComparisonMode.contains.compare("12345", "**2345***"));
    }

    @Test
    void contains_wildcardInMiddleOfPattern() {
        assertTrue(ScriptComparator.ComparisonMode.contains.compare("abcdef", "c*e"));
    }

    @Test
    void contains_wildcardMatches() {
        assertTrue(ScriptComparator.ComparisonMode.contains.compare("abcdef", "a*c"));
    }

    @Test
    void contains_patternLongerThanText() {
        assertFalse(ScriptComparator.ComparisonMode.contains.compare("hi", "hello"));
    }

    @Test
    void contains_emptyPattern() {
        assertTrue(ScriptComparator.ComparisonMode.contains.compare("hello", ""));
    }

    @Test
    void contains_emptyTextContainsNothing() {
        assertTrue(ScriptComparator.ComparisonMode.contains.compare("", ""));
    }

    @Test
    void contains_patternLongerThanEmptyText() {
        assertTrue(ScriptComparator.ComparisonMode.contains.compare("", "hello"));
    }

    @Test
    void contains_bothEmpty() {
        assertTrue(ScriptComparator.ComparisonMode.contains.compare("", ""));
    }

    @Test
    void contains_allWildcardsPattern() {
        assertTrue(ScriptComparator.ComparisonMode.contains.compare("hello", "***"));
    }

    @Test
    void contains_bidirectionalFirstContainsSecond() {
        assertTrue(ScriptComparator.ComparisonMode.contains.compare("hello world", "world"));
    }

    @Test
    void contains_bidirectionalSecondContainsFirst() {
        assertTrue(ScriptComparator.ComparisonMode.contains.compare("world", "hello world"));
    }

    @Test
    void contains_wildcardAtStartAndEnd() {
        assertTrue(ScriptComparator.ComparisonMode.contains.compare("abcdef", "*cd*"));
    }

    @Test
    void contains_multipleWildcardsInPattern() {
        assertTrue(ScriptComparator.ComparisonMode.contains.compare("test123", "t**t"));
    }

    @Test
    void contains_singleCharacterWildcard() {
        assertTrue(ScriptComparator.ComparisonMode.contains.compare("abc", "*"));
    }
}

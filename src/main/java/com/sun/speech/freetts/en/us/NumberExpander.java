//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.sun.speech.freetts.en.us;

import com.sun.speech.freetts.FeatureSet;
import com.sun.speech.freetts.Item;
import com.sun.speech.freetts.util.Utilities;

public class NumberExpander {
    private static final String[] digit2num = new String[]{"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};
    private static final String[] digit2teen = new String[]{"ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
    private static final String[] digit2enty = new String[]{"zero", "ten", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};
    private static final String[] ord2num = new String[]{"zeroth", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth"};
    private static final String[] ord2teen = new String[]{"tenth", "eleventh", "twelfth", "thirteenth", "fourteenth", "fifteenth", "sixteenth", "seventeenth", "eighteenth", "nineteenth"};
    private static final String[] ord2enty = new String[]{"zeroth", "tenth", "twentieth", "thirtieth", "fortieth", "fiftieth", "sixtieth", "seventieth", "eightieth", "ninetieth"};

    private NumberExpander() {
    }

    public static void expandNumber(String numberString, WordRelation wordRelation) {
        int numDigits = numberString.length();
        if (numDigits != 0) {
            if (numDigits == 1) {
                expandDigits(numberString, wordRelation);
            } else if (numDigits == 2) {
                expand2DigitNumber(numberString, wordRelation);
            } else if (numDigits == 3) {
                expand3DigitNumber(numberString, wordRelation);
            } else if (numDigits < 7) {
                expandBelow7DigitNumber(numberString, wordRelation);
            } else if (numDigits < 10) {
                expandBelow10DigitNumber(numberString, wordRelation);
            } else if (numDigits < 13) {
                expandBelow13DigitNumber(numberString, wordRelation);
            } else {
                expandDigits(numberString, wordRelation);
            }
        }

    }

    private static void expand2DigitNumber(String numberString, WordRelation wordRelation) {
        if (numberString.charAt(0) == '0') {
            if (numberString.charAt(1) != '0') {
                String number = digit2num[numberString.charAt(1) - 48];
                wordRelation.addWord(number);
            }
        } else if (numberString.charAt(1) == '0') {
            String number = digit2enty[numberString.charAt(0) - 48];
            wordRelation.addWord(number);
        } else if (numberString.charAt(0) == '1') {
            String number = digit2teen[numberString.charAt(1) - 48];
            wordRelation.addWord(number);
        } else {
            String enty = digit2enty[numberString.charAt(0) - 48];
            wordRelation.addWord(enty);
            expandDigits(numberString.substring(1, numberString.length()), wordRelation);
        }

    }

    private static void expand3DigitNumber(String numberString, WordRelation wordRelation) {
        if (numberString.charAt(0) == '0') {
            expandNumberAt(numberString, 1, wordRelation);
        } else {
            String hundredDigit = digit2num[numberString.charAt(0) - 48];
            wordRelation.addWord(hundredDigit);
            wordRelation.addWord("hundred");
            expandNumberAt(numberString, 1, wordRelation);
        }

    }

    private static void expandBelow7DigitNumber(String numberString, WordRelation wordRelation) {
        expandLargeNumber(numberString, "thousand", 3, wordRelation);
    }

    private static void expandBelow10DigitNumber(String numberString, WordRelation wordRelation) {
        expandLargeNumber(numberString, "million", 6, wordRelation);
    }

    private static void expandBelow13DigitNumber(String numberString, WordRelation wordRelation) {
        expandLargeNumber(numberString, "billion", 9, wordRelation);
    }

    private static void expandLargeNumber(String numberString, String order, int numberZeroes, WordRelation wordRelation) {
        int numberDigits = numberString.length();
        int i = numberDigits - numberZeroes;
        String part = numberString.substring(0, i);
        Item oldTail = wordRelation.getTail();
        expandNumber(part, wordRelation);
        if (wordRelation.getTail() == oldTail) {
            expandNumberAt(numberString, i, wordRelation);
        } else {
            wordRelation.addWord(order);
            expandNumberAt(numberString, i, wordRelation);
        }

    }

    private static void expandNumberAt(String numberString, int startIndex, WordRelation wordRelation) {
        expandNumber(numberString.substring(startIndex, numberString.length()), wordRelation);
    }

    public static void expandDigits(String numberString, WordRelation wordRelation) {
        int numberDigits = numberString.length();

        for(int i = 0; i < numberDigits; ++i) {
            char digit = numberString.charAt(i);
            if (isDigit(digit)) {
                wordRelation.addWord(digit2num[numberString.charAt(i) - 48]);
            } else {
                wordRelation.addWord("umpty");
            }
        }

    }

    public static void expandOrdinal(String rawNumberString, WordRelation wordRelation) {
        String numberString = Utilities.deleteChar(rawNumberString, ',');
        expandNumber(numberString, wordRelation);
        Item lastItem = wordRelation.getTail();
        if (lastItem != null) {
            FeatureSet featureSet = lastItem.getFeatures();
            String lastNumber = featureSet.getString("name");
            String ordinal = findMatchInArray(lastNumber, digit2num, ord2num);
            if (ordinal == null) {
                ordinal = findMatchInArray(lastNumber, digit2teen, ord2teen);
            }

            if (ordinal == null) {
                ordinal = findMatchInArray(lastNumber, digit2enty, ord2enty);
            }

            if (lastNumber.equals("hundred")) {
                ordinal = "hundredth";
            } else if (lastNumber.equals("thousand")) {
                ordinal = "thousandth";
            } else if (lastNumber.equals("billion")) {
                ordinal = "billionth";
            }

            if (ordinal != null) {
                wordRelation.setLastWord(ordinal);
            }
        }

    }

    private static String findMatchInArray(String strToMatch, String[] matchInArray, String[] returnInArray) {
        for(int i = 0; i < matchInArray.length; ++i) {
            if (strToMatch.equals(matchInArray[i])) {
                if (i < returnInArray.length) {
                    return returnInArray[i];
                }

                return null;
            }
        }

        return null;
    }

    public static void expandID(String numberString, WordRelation wordRelation) {
        int numberDigits = numberString.length();
        if (numberDigits == 4 && numberString.charAt(2) == '0' && numberString.charAt(3) == '0') {
            if (numberString.charAt(1) == '0') {
                expandNumber(numberString, wordRelation);
            } else {
                expandNumber(numberString.substring(0, 2), wordRelation);
                wordRelation.addWord("hundred");
            }
        } else if (numberDigits == 2 && numberString.charAt(0) == '0') {
            wordRelation.addWord("oh");
            expandDigits(numberString.substring(1, 2), wordRelation);
        } else if ((numberDigits != 4 || numberString.charAt(1) != '0') && numberDigits >= 3) {
            if (numberDigits % 2 == 1) {
                String firstDigit = digit2num[numberString.charAt(0) - 48];
                wordRelation.addWord(firstDigit);
                expandID(numberString.substring(1, numberDigits), wordRelation);
            } else {
                expandNumber(numberString.substring(0, 2), wordRelation);
                expandID(numberString.substring(2, numberDigits), wordRelation);
            }
        } else {
            expandNumber(numberString, wordRelation);
        }

    }

    public static void expandReal(String numberString, WordRelation wordRelation) {
        int stringLength = numberString.length();
        if (numberString.charAt(0) == '-') {
            wordRelation.addWord("minus");
            expandReal(numberString.substring(1, stringLength), wordRelation);
        } else if (numberString.charAt(0) == '+') {
            wordRelation.addWord("plus");
            expandReal(numberString.substring(1, stringLength), wordRelation);
        } else {
            int position;
            if ((position = numberString.indexOf(101)) == -1 && (position = numberString.indexOf(69)) == -1) {
                if ((position = numberString.indexOf(46)) != -1) {
                    String beforeDot = numberString.substring(0, position);
                    if (beforeDot.length() > 0) {
                        expandReal(beforeDot, wordRelation);
                    }

                    wordRelation.addWord("point");
                    String afterDot = numberString.substring(position + 1);
                    if (afterDot.length() > 0) {
                        expandDigits(afterDot, wordRelation);
                    }
                } else {
                    expandNumber(numberString, wordRelation);
                }
            } else {
                expandReal(numberString.substring(0, position), wordRelation);
                wordRelation.addWord("e");
                expandReal(numberString.substring(position + 1), wordRelation);
            }
        }

    }

    public static void expandLetters(String letters, WordRelation wordRelation) {
        letters = letters.toLowerCase();

        for(int i = 0; i < letters.length(); ++i) {
            char c = letters.charAt(i);
            if (isDigit(c)) {
                wordRelation.addWord(digit2num[c - 48]);
            } else if (letters.equals("a")) {
                wordRelation.addWord("_a");
            } else {
                wordRelation.addWord(String.valueOf(c));
            }
        }

    }

    public static int expandRoman(String roman) {
        int value = 0;

        for(int p = 0; p < roman.length(); ++p) {
            char c = roman.charAt(p);
            if (c == 'X') {
                value += 10;
            } else if (c == 'V') {
                value += 5;
            } else if (c == 'I') {
                if (p + 1 < roman.length()) {
                    char p1 = roman.charAt(p + 1);
                    if (p1 == 'V') {
                        value += 4;
                        ++p;
                    } else if (p1 == 'X') {
                        value += 9;
                        ++p;
                    } else {
                        ++value;
                    }
                } else {
                    ++value;
                }
            }
        }

        return value;
    }

    public static boolean isDigit(char ch) {
        return '0' <= ch && ch <= '9';
    }
}

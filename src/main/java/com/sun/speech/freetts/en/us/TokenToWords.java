//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.sun.speech.freetts.en.us;

import com.sun.speech.freetts.FeatureSet;
import com.sun.speech.freetts.Item;
import com.sun.speech.freetts.ProcessException;
import com.sun.speech.freetts.Relation;
import com.sun.speech.freetts.Utterance;
import com.sun.speech.freetts.UtteranceProcessor;
import com.sun.speech.freetts.cart.CART;
import com.sun.speech.freetts.util.Utilities;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TokenToWords implements UtteranceProcessor {
    private static final String RX_HAS_VOWEL = ".*[aeiouAEIOU].*";
    private static final Pattern alphabetPattern;
    private static final Pattern commaIntPattern;
    private static final Pattern digits2DashPattern;
    private static final Pattern digitsPattern;
    private static final Pattern digitsSlashDigitsPattern;
    private static final Pattern dottedAbbrevPattern;
    private static final Pattern doublePattern;
    private static final Pattern drStPattern;
    private static final Pattern fourDigitsPattern;
    private static final Pattern hasVowelPattern;
    private static final Pattern illionPattern;
    private static final Pattern numberTimePattern;
    private static final Pattern numessPattern;
    private static final Pattern ordinalPattern;
    private static final Pattern romanNumbersPattern;
    private static final Pattern sevenPhoneNumberPattern;
    private static final Pattern threeDigitsPattern;
    private static final Pattern usMoneyPattern;
    private static final String[] kingNames;
    private static final String[] kingTitles;
    private static final String[] sectionTypes;
    private static Hashtable kingSectionLikeHash;
    private static final String KING_NAMES = "kingNames";
    private static final String KING_TITLES = "kingTitles";
    private static final String SECTION_TYPES = "sectionTypes";
    private static final String[] postrophes;
    private PronounceableFSM prefixFSM = null;
    private PronounceableFSM suffixFSM = null;
    private static final String[][] usStates;
    private static Hashtable usStatesHash;
    private WordRelation wordRelation;
    private Item tokenItem;
    private CART cart;

    public TokenToWords(CART usNumbersCART, PronounceableFSM prefixFSM, PronounceableFSM suffixFSM) {
        this.cart = usNumbersCART;
        this.prefixFSM = prefixFSM;
        this.suffixFSM = suffixFSM;
    }

    public Item getTokenItem() {
        return this.tokenItem;
    }

    public void processUtterance(Utterance utterance) throws ProcessException {
        Relation tokenRelation;
        if ((tokenRelation = utterance.getRelation("Token")) == null) {
            throw new IllegalStateException("TokenToWords: Token relation does not exist");
        } else {
            this.wordRelation = WordRelation.createWordRelation(utterance, this);

            for(this.tokenItem = tokenRelation.getHead(); this.tokenItem != null; this.tokenItem = this.tokenItem.getNext()) {
                FeatureSet featureSet = this.tokenItem.getFeatures();
                String tokenVal = featureSet.getString("name");
                this.tokenToWords(tokenVal);
            }

        }
    }

    private boolean matchesPartPhoneNumber(String tokenVal) {
        String n_name = (String)this.tokenItem.findFeature("n.name");
        String n_n_name = (String)this.tokenItem.findFeature("n.n.name");
        String p_name = (String)this.tokenItem.findFeature("p.name");
        String p_p_name = (String)this.tokenItem.findFeature("p.p.name");
        boolean matches3DigitsP_name = matches(threeDigitsPattern, p_name);
        return matches(threeDigitsPattern, tokenVal) && (!matches(digitsPattern, p_name) && matches(threeDigitsPattern, n_name) && matches(fourDigitsPattern, n_n_name) || matches(sevenPhoneNumberPattern, n_name) || !matches(digitsPattern, p_p_name) && matches3DigitsP_name && matches(fourDigitsPattern, n_name)) || matches(fourDigitsPattern, tokenVal) && !matches(digitsPattern, n_name) && matches3DigitsP_name && matches(threeDigitsPattern, p_p_name);
    }

    private static boolean inStringArray(String value, String[] stringArray) {
        for(int i = 0; i < stringArray.length; ++i) {
            if (stringArray[i].equals(value)) {
                return true;
            }
        }

        return false;
    }

    private void tokenToWords(String tokenVal) {
        FeatureSet tokenFeatures = this.tokenItem.getFeatures();
        String itemName = tokenFeatures.getString("name");
        int tokenLength = tokenVal.length();
        if (tokenFeatures.isPresent("phones")) {
            this.wordRelation.addWord(tokenVal);
        } else if (!tokenVal.equals("a") && !tokenVal.equals("A") || this.tokenItem.getNext() != null && tokenVal.equals(itemName) && ((String)this.tokenItem.findFeature("punc")).equals("")) {
            if (matches(alphabetPattern, tokenVal)) {
                if (matches(romanNumbersPattern, tokenVal)) {
                    this.romanToWords(tokenVal);
                } else if (matches(illionPattern, tokenVal) && matches(usMoneyPattern, (String)this.tokenItem.findFeature("p.name"))) {
                    this.wordRelation.addWord(tokenVal);
                    this.wordRelation.addWord("dollars");
                } else if (matches(drStPattern, tokenVal)) {
                    this.drStToWords(tokenVal);
                } else if (tokenVal.equals("Mr")) {
                    this.tokenItem.getFeatures().setString("punc", "");
                    this.wordRelation.addWord("mister");
                } else if (tokenVal.equals("Mrs")) {
                    this.tokenItem.getFeatures().setString("punc", "");
                    this.wordRelation.addWord("missus");
                } else if (tokenLength == 1 && isUppercaseLetter(tokenVal.charAt(0)) && ((String)this.tokenItem.findFeature("n.whitespace")).equals(" ") && isUppercaseLetter(((String)this.tokenItem.findFeature("n.name")).charAt(0))) {
                    tokenFeatures.setString("punc", "");
                    String aaa = tokenVal.toLowerCase();
                    if (aaa.equals("a")) {
                        this.wordRelation.addWord("_a");
                    } else {
                        this.wordRelation.addWord(aaa);
                    }
                } else if (!this.isStateName(tokenVal)) {
                    if (tokenLength > 1 && !this.isPronounceable(tokenVal)) {
                        NumberExpander.expandLetters(tokenVal, this.wordRelation);
                    } else {
                        this.wordRelation.addWord(tokenVal.toLowerCase());
                    }
                }
            } else if (matches(dottedAbbrevPattern, tokenVal)) {
                String aaa = Utilities.deleteChar(tokenVal, '.');
                NumberExpander.expandLetters(aaa, this.wordRelation);
            } else if (matches(commaIntPattern, tokenVal)) {
                String aaa = Utilities.deleteChar(tokenVal, ',');
                NumberExpander.expandReal(aaa, this.wordRelation);
            } else if (matches(sevenPhoneNumberPattern, tokenVal)) {
                int dashIndex = tokenVal.indexOf(45);
                String aaa = tokenVal.substring(0, dashIndex);
                String bbb = tokenVal.substring(dashIndex + 1);
                NumberExpander.expandDigits(aaa, this.wordRelation);
                this.wordRelation.addBreak();
                NumberExpander.expandDigits(bbb, this.wordRelation);
            } else if (this.matchesPartPhoneNumber(tokenVal)) {
                String punctuation = (String)this.tokenItem.findFeature("punc");
                if (punctuation.equals("")) {
                    this.tokenItem.getFeatures().setString("punc", ",");
                }

                NumberExpander.expandDigits(tokenVal, this.wordRelation);
                this.wordRelation.addBreak();
            } else if (matches(numberTimePattern, tokenVal)) {
                int colonIndex = tokenVal.indexOf(58);
                String aaa = tokenVal.substring(0, colonIndex);
                String bbb = tokenVal.substring(colonIndex + 1);
                NumberExpander.expandNumber(aaa, this.wordRelation);
                if (!bbb.equals("00")) {
                    NumberExpander.expandID(bbb, this.wordRelation);
                }
            } else if (matches(digits2DashPattern, tokenVal)) {
                this.digitsDashToWords(tokenVal);
            } else if (matches(digitsPattern, tokenVal)) {
                this.digitsToWords(tokenVal);
            } else if (tokenLength == 1 && isUppercaseLetter(tokenVal.charAt(0)) && ((String)this.tokenItem.findFeature("n.whitespace")).equals(" ") && isUppercaseLetter(((String)this.tokenItem.findFeature("n.name")).charAt(0))) {
                tokenFeatures.setString("punc", "");
                String aaa = tokenVal.toLowerCase();
                if (aaa.equals("a")) {
                    this.wordRelation.addWord("_a");
                } else {
                    this.wordRelation.addWord(aaa);
                }
            } else if (matches(doublePattern, tokenVal)) {
                NumberExpander.expandReal(tokenVal, this.wordRelation);
            } else if (matches(ordinalPattern, tokenVal)) {
                String aaa = tokenVal.substring(0, tokenLength - 2);
                NumberExpander.expandOrdinal(aaa, this.wordRelation);
            } else if (matches(usMoneyPattern, tokenVal)) {
                this.usMoneyToWords(tokenVal);
            } else if (tokenLength > 0 && tokenVal.charAt(tokenLength - 1) == '%') {
                this.tokenToWords(tokenVal.substring(0, tokenLength - 1));
                this.wordRelation.addWord("per");
                this.wordRelation.addWord("cent");
            } else if (matches(numessPattern, tokenVal)) {
                this.tokenToWords(tokenVal.substring(0, tokenLength - 1));
                this.wordRelation.addWord("'s");
            } else if (tokenVal.indexOf(39) != -1) {
                this.postropheToWords(tokenVal);
            } else if (matches(digitsSlashDigitsPattern, tokenVal) && tokenVal.equals(itemName)) {
                this.digitsSlashDigitsToWords(tokenVal);
            } else if (tokenVal.indexOf(45) != -1) {
                this.dashToWords(tokenVal);
            } else if (tokenLength > 1 && !matches(alphabetPattern, tokenVal)) {
                this.notJustAlphasToWords(tokenVal);
            } else {
                this.wordRelation.addWord(tokenVal.toLowerCase());
            }
        } else {
            this.wordRelation.addWord("_a");
        }

    }

    private void digitsDashToWords(String tokenVal) {
        int tokenLength = tokenVal.length();
        int a = 0;

        for(int p = 0; p <= tokenLength; ++p) {
            if (p == tokenLength || tokenVal.charAt(p) == '-') {
                String aaa = tokenVal.substring(a, p);
                NumberExpander.expandDigits(aaa, this.wordRelation);
                this.wordRelation.addBreak();
                a = p + 1;
            }
        }

    }

    private void digitsToWords(String tokenVal) {
        FeatureSet featureSet = this.tokenItem.getFeatures();
        String nsw = "";
        if (featureSet.isPresent("nsw")) {
            nsw = featureSet.getString("nsw");
        }

        if (nsw.equals("nide")) {
            NumberExpander.expandID(tokenVal, this.wordRelation);
        } else {
            String rName = featureSet.getString("name");
            String digitsType = null;
            if (tokenVal.equals(rName)) {
                digitsType = (String)this.cart.interpret(this.tokenItem);
            } else {
                featureSet.setString("name", tokenVal);
                digitsType = (String)this.cart.interpret(this.tokenItem);
                featureSet.setString("name", rName);
            }

            if (digitsType.equals("ordinal")) {
                NumberExpander.expandOrdinal(tokenVal, this.wordRelation);
            } else if (digitsType.equals("digits")) {
                NumberExpander.expandDigits(tokenVal, this.wordRelation);
            } else if (digitsType.equals("year")) {
                NumberExpander.expandID(tokenVal, this.wordRelation);
            } else {
                NumberExpander.expandNumber(tokenVal, this.wordRelation);
            }
        }

    }

    private void romanToWords(String romanString) {
        String punctuation = (String)this.tokenItem.findFeature("p.punc");
        if (punctuation.equals("")) {
            String n = String.valueOf(NumberExpander.expandRoman(romanString));
            if (kingLike(this.tokenItem)) {
                this.wordRelation.addWord("the");
                NumberExpander.expandOrdinal(n, this.wordRelation);
            } else if (sectionLike(this.tokenItem)) {
                NumberExpander.expandNumber(n, this.wordRelation);
            } else {
                NumberExpander.expandLetters(romanString, this.wordRelation);
            }
        } else {
            NumberExpander.expandLetters(romanString, this.wordRelation);
        }

    }

    private static boolean inKingSectionLikeHash(String key, String value) {
        String hashValue = (String)kingSectionLikeHash.get(key);
        return hashValue != null ? hashValue.equals(value) : false;
    }

    public static boolean kingLike(Item tokenItem) {
        String kingName = ((String)tokenItem.findFeature("p.name")).toLowerCase();
        if (inKingSectionLikeHash(kingName, "kingNames")) {
            return true;
        } else {
            String kingTitle = ((String)tokenItem.findFeature("p.p.name")).toLowerCase();
            return inKingSectionLikeHash(kingTitle, "kingTitles");
        }
    }

    public static boolean sectionLike(Item tokenItem) {
        String sectionType = ((String)tokenItem.findFeature("p.name")).toLowerCase();
        return inKingSectionLikeHash(sectionType, "sectionTypes");
    }

    private void drStToWords(String drStString) {
        String street = null;
        String saint = null;
        char c0 = drStString.charAt(0);
        if (c0 != 's' && c0 != 'S') {
            street = "drive";
            saint = "doctor";
        } else {
            street = "street";
            saint = "saint";
        }

        FeatureSet featureSet = this.tokenItem.getFeatures();
        String punctuation = featureSet.getString("punc");
        String featPunctuation = (String)this.tokenItem.findFeature("punc");
        if (this.tokenItem.getNext() != null && punctuation.indexOf(44) == -1) {
            if (featPunctuation.equals(",")) {
                this.wordRelation.addWord(saint);
            } else {
                String pName = (String)this.tokenItem.findFeature("p.name");
                String nName = (String)this.tokenItem.findFeature("n.name");
                char p0 = pName.charAt(0);
                char n0 = nName.charAt(0);
                if (isUppercaseLetter(p0) && isLowercaseLetter(n0)) {
                    this.wordRelation.addWord(street);
                } else if (NumberExpander.isDigit(p0) && isLowercaseLetter(n0)) {
                    this.wordRelation.addWord(street);
                } else if (isLowercaseLetter(p0) && isUppercaseLetter(n0)) {
                    this.wordRelation.addWord(saint);
                } else {
                    String whitespace = (String)this.tokenItem.findFeature("n.whitespace");
                    if (whitespace.equals(" ")) {
                        this.wordRelation.addWord(saint);
                    } else {
                        this.wordRelation.addWord(street);
                    }
                }
            }
        } else {
            this.wordRelation.addWord(street);
        }

        if (punctuation != null && punctuation.equals(".")) {
            featureSet.setString("punc", "");
        }

    }

    private void usMoneyToWords(String tokenVal) {
        int dotIndex = tokenVal.indexOf(46);
        if (matches(illionPattern, (String)this.tokenItem.findFeature("n.name"))) {
            NumberExpander.expandReal(tokenVal.substring(1), this.wordRelation);
        } else if (dotIndex == -1) {
            String aaa = tokenVal.substring(1);
            this.tokenToWords(aaa);
            if (aaa.equals("1")) {
                this.wordRelation.addWord("dollar");
            } else {
                this.wordRelation.addWord("dollars");
            }
        } else if (dotIndex != tokenVal.length() - 1 && tokenVal.length() - dotIndex <= 3) {
            String aaa = tokenVal.substring(1, dotIndex);
            aaa = Utilities.deleteChar(aaa, ',');
            String bbb = tokenVal.substring(dotIndex + 1);
            NumberExpander.expandNumber(aaa, this.wordRelation);
            if (aaa.equals("1")) {
                this.wordRelation.addWord("dollar");
            } else {
                this.wordRelation.addWord("dollars");
            }

            if (!bbb.equals("00")) {
                NumberExpander.expandNumber(bbb, this.wordRelation);
                if (bbb.equals("01")) {
                    this.wordRelation.addWord("cent");
                } else {
                    this.wordRelation.addWord("cents");
                }
            }
        } else {
            NumberExpander.expandReal(tokenVal.substring(1), this.wordRelation);
            this.wordRelation.addWord("dollars");
        }

    }

    private void postropheToWords(String tokenVal) {
        int index = tokenVal.indexOf(39);
        String bbb = tokenVal.substring(index).toLowerCase();
        if (inStringArray(bbb, postrophes)) {
            String aaa = tokenVal.substring(0, index);
            this.tokenToWords(aaa);
            this.wordRelation.addWord(bbb);
        } else if (bbb.equals("'tve")) {
            String aaa = tokenVal.substring(0, index - 2);
            this.tokenToWords(aaa);
            this.wordRelation.addWord("'ve");
        } else {
            StringBuffer buffer = new StringBuffer(tokenVal);
            buffer.deleteCharAt(index);
            this.tokenToWords(buffer.toString());
        }

    }

    private void digitsSlashDigitsToWords(String tokenVal) {
        int index = tokenVal.indexOf(47);
        String aaa = tokenVal.substring(0, index);
        String bbb = tokenVal.substring(index + 1);
        if (matches(digitsPattern, (String)this.tokenItem.findFeature("p.name")) && this.tokenItem.getPrevious() != null) {
            this.wordRelation.addWord("and");
        }

        if (aaa.equals("1") && bbb.equals("2")) {
            this.wordRelation.addWord("a");
            this.wordRelation.addWord("half");
        } else {
            int a;
            if ((a = Integer.parseInt(aaa)) < Integer.parseInt(bbb)) {
                NumberExpander.expandNumber(aaa, this.wordRelation);
                NumberExpander.expandOrdinal(bbb, this.wordRelation);
                if (a > 1) {
                    this.wordRelation.addWord("'s");
                }
            } else {
                NumberExpander.expandNumber(aaa, this.wordRelation);
                this.wordRelation.addWord("slash");
                NumberExpander.expandNumber(bbb, this.wordRelation);
            }
        }

    }

    private void dashToWords(String tokenVal) {
        int index = tokenVal.indexOf(45);
        String aaa = tokenVal.substring(0, index);
        String bbb = tokenVal.substring(index + 1, tokenVal.length());
        if (matches(digitsPattern, aaa) && matches(digitsPattern, bbb)) {
            FeatureSet featureSet = this.tokenItem.getFeatures();
            featureSet.setString("name", aaa);
            this.tokenToWords(aaa);
            this.wordRelation.addWord("to");
            featureSet.setString("name", bbb);
            this.tokenToWords(bbb);
            featureSet.setString("name", "");
        } else {
            this.tokenToWords(aaa);
            this.tokenToWords(bbb);
        }

    }

    private void notJustAlphasToWords(String tokenVal) {
        int index = 0;

        int tokenLength;
        for(tokenLength = tokenVal.length(); index < tokenLength && !isTextSplitable(tokenVal, index); ++index) {
        }

        String aaa = tokenVal.substring(0, index + 1);
        String bbb = tokenVal.substring(index + 1, tokenLength);
        FeatureSet featureSet = this.tokenItem.getFeatures();
        featureSet.setString("nsw", "nide");
        this.tokenToWords(aaa);
        this.tokenToWords(bbb);
    }

    public boolean isPronounceable(String word) {
        String lowerCaseWord = word.toLowerCase();
        return this.prefixFSM.accept(lowerCaseWord) && this.suffixFSM.accept(lowerCaseWord);
    }

    private boolean isStateName(String tokenVal) {
        String[] state = (String[])usStatesHash.get(tokenVal);
        if (state != null) {
            boolean expandState = false;
            if (!state[1].equals("ambiguous")) {
                expandState = true;
            } else {
                String previous = (String)this.tokenItem.findFeature("p.name");
                String next = (String)this.tokenItem.findFeature("n.name");
                int nextLength = next.length();
                FeatureSet featureSet = this.tokenItem.getFeatures();
                boolean previousIsCity = isUppercaseLetter(previous.charAt(0)) && previous.length() > 2 && matches(alphabetPattern, previous) && this.tokenItem.findFeature("p.punc").equals(",");
                boolean nextIsGood = isLowercaseLetter(next.charAt(0)) || this.tokenItem.getNext() == null || featureSet.getString("punc").equals(".") || (nextLength == 5 || nextLength == 10) && matches(digitsPattern, next);
                if (previousIsCity && nextIsGood) {
                    expandState = true;
                } else {
                    expandState = false;
                }
            }

            if (expandState) {
                for(int j = 2; j < state.length; ++j) {
                    if (state[j] != null) {
                        this.wordRelation.addWord(state[j]);
                    }
                }

                return true;
            }
        }

        return false;
    }

    private static boolean matches(Pattern pattern, String input) {
        Matcher m = pattern.matcher(input);
        return m.matches();
    }

    private static boolean isTextSplitable(String text, int index) {
        char c0 = text.charAt(index);
        char c1 = text.charAt(index + 1);
        if (isLetter(c0) && isLetter(c1)) {
            return false;
        } else {
            return !NumberExpander.isDigit(c0) || !NumberExpander.isDigit(c1);
        }
    }

    private static boolean isLetter(char ch) {
        return 'a' <= ch && ch <= 'z' || 'A' <= ch && ch <= 'Z';
    }

    private static boolean isUppercaseLetter(char ch) {
        return 'A' <= ch && ch <= 'Z';
    }

    private static boolean isLowercaseLetter(char ch) {
        return 'a' <= ch && ch <= 'z';
    }

    public String toString() {
        return "TokenToWords";
    }

    static {
        alphabetPattern = Pattern.compile(USEnglish.RX_ALPHABET);
        commaIntPattern = Pattern.compile(USEnglish.RX_COMMAINT);
        digits2DashPattern = Pattern.compile("[0-9]+(-[0-9]+)(-[0-9]+)+");
        digitsPattern = Pattern.compile(USEnglish.RX_DIGITS);
        digitsSlashDigitsPattern = Pattern.compile("[0-9]+/[0-9]+");
        dottedAbbrevPattern = Pattern.compile(USEnglish.RX_DOTTED_ABBREV);
        doublePattern = Pattern.compile(USEnglish.RX_DOUBLE);
        drStPattern = Pattern.compile("([dD][Rr]|[Ss][Tt])");
        fourDigitsPattern = Pattern.compile("[0-9][0-9][0-9][0-9]");
        hasVowelPattern = Pattern.compile(".*[aeiouAEIOU].*");
        illionPattern = Pattern.compile(".*illion");
        numberTimePattern = Pattern.compile("((0[0-2])|(1[0-9])):([0-5][0-9])");
        numessPattern = Pattern.compile("[0-9]+s");
        ordinalPattern = Pattern.compile(USEnglish.RX_ORDINAL_NUMBER);
        romanNumbersPattern = Pattern.compile("(II?I?|IV|VI?I?I?|IX|X[VIX]*)");
        sevenPhoneNumberPattern = Pattern.compile("[0-9][0-9][0-9]-[0-9][0-9][0-9][0-9]");
        threeDigitsPattern = Pattern.compile("[0-9][0-9][0-9]");
        usMoneyPattern = Pattern.compile("\\$[0-9,]+(\\.[0-9]+)?");
        kingNames = new String[]{"louis", "henry", "charles", "philip", "george", "edward", "pius", "william", "richard", "ptolemy", "john", "paul", "peter", "nicholas", "frederick", "james", "alfonso", "ivan", "napoleon", "leo", "gregory", "catherine", "alexandria", "pierre", "elizabeth", "mary"};
        kingTitles = new String[]{"king", "queen", "pope", "duke", "tsar", "emperor", "shah", "caesar", "duchess", "tsarina", "empress", "baron", "baroness", "sultan", "count", "countess"};
        sectionTypes = new String[]{"section", "chapter", "part", "phrase", "verse", "scene", "act", "book", "volume", "chap", "war", "apollo", "trek", "fortran"};
        kingSectionLikeHash = new Hashtable();

        for(int i = 0; i < kingNames.length; ++i) {
            kingSectionLikeHash.put(kingNames[i], "kingNames");
        }

        for(int i = 0; i < kingTitles.length; ++i) {
            kingSectionLikeHash.put(kingTitles[i], "kingTitles");
        }

        for(int i = 0; i < sectionTypes.length; ++i) {
            kingSectionLikeHash.put(sectionTypes[i], "sectionTypes");
        }

        postrophes = new String[]{"'s", "'ll", "'ve", "'d"};
        usStates = new String[][]{{"AL", "ambiguous", "alabama"}, {"Al", "ambiguous", "alabama"}, {"Ala", "", "alabama"}, {"AK", "", "alaska"}, {"Ak", "", "alaska"}, {"AZ", "", "arizona"}, {"Az", "", "arizona"}, {"CA", "", "california"}, {"Ca", "", "california"}, {"Cal", "ambiguous", "california"}, {"Calif", "", "california"}, {"CO", "ambiguous", "colorado"}, {"Co", "ambiguous", "colorado"}, {"Colo", "", "colorado"}, {"DC", "", "d", "c"}, {"DE", "", "delaware"}, {"De", "ambiguous", "delaware"}, {"Del", "ambiguous", "delaware"}, {"FL", "", "florida"}, {"Fl", "ambiguous", "florida"}, {"Fla", "", "florida"}, {"GA", "", "georgia"}, {"Ga", "", "georgia"}, {"HI", "ambiguous", "hawaii"}, {"Hi", "ambiguous", "hawaii"}, {"IA", "", "iowa"}, {"Ia", "ambiguous", "iowa"}, {"IN", "ambiguous", "indiana"}, {"In", "ambiguous", "indiana"}, {"Ind", "ambiguous", "indiana"}, {"ID", "ambiguous", "idaho"}, {"IL", "ambiguous", "illinois"}, {"Il", "ambiguous", "illinois"}, {"ILL", "ambiguous", "illinois"}, {"KS", "", "kansas"}, {"Ks", "", "kansas"}, {"Kans", "", "kansas"}, {"KY", "ambiguous", "kentucky"}, {"Ky", "ambiguous", "kentucky"}, {"LA", "ambiguous", "louisiana"}, {"La", "ambiguous", "louisiana"}, {"Lou", "ambiguous", "louisiana"}, {"Lous", "ambiguous", "louisiana"}, {"MA", "ambiguous", "massachusetts"}, {"Mass", "ambiguous", "massachusetts"}, {"Ma", "ambiguous", "massachusetts"}, {"MD", "ambiguous", "maryland"}, {"Md", "ambiguous", "maryland"}, {"ME", "ambiguous", "maine"}, {"Me", "ambiguous", "maine"}, {"MI", "", "michigan"}, {"Mi", "ambiguous", "michigan"}, {"Mich", "ambiguous", "michigan"}, {"MN", "ambiguous", "minnestota"}, {"Minn", "ambiguous", "minnestota"}, {"MS", "ambiguous", "mississippi"}, {"Miss", "ambiguous", "mississippi"}, {"MT", "ambiguous", "montanna"}, {"Mt", "ambiguous", "montanna"}, {"MO", "ambiguous", "missouri"}, {"Mo", "ambiguous", "missouri"}, {"NC", "ambiguous", "north", "carolina"}, {"ND", "ambiguous", "north", "dakota"}, {"NE", "ambiguous", "nebraska"}, {"Ne", "ambiguous", "nebraska"}, {"Neb", "ambiguous", "nebraska"}, {"NH", "ambiguous", "new", "hampshire"}, {"NV", "", "nevada"}, {"Nev", "", "nevada"}, {"NY", "", "new", "york"}, {"OH", "ambiguous", "ohio"}, {"OK", "ambiguous", "oklahoma"}, {"Okla", "", "oklahoma"}, {"OR", "ambiguous", "oregon"}, {"Or", "ambiguous", "oregon"}, {"Ore", "ambiguous", "oregon"}, {"PA", "ambiguous", "pennsylvania"}, {"Pa", "ambiguous", "pennsylvania"}, {"Penn", "ambiguous", "pennsylvania"}, {"RI", "ambiguous", "rhode", "island"}, {"SC", "ambiguous", "south", "carlolina"}, {"SD", "ambiguous", "south", "dakota"}, {"TN", "ambiguous", "tennesee"}, {"Tn", "ambiguous", "tennesee"}, {"Tenn", "ambiguous", "tennesee"}, {"TX", "ambiguous", "texas"}, {"Tx", "ambiguous", "texas"}, {"Tex", "ambiguous", "texas"}, {"UT", "ambiguous", "utah"}, {"VA", "ambiguous", "virginia"}, {"WA", "ambiguous", "washington"}, {"Wa", "ambiguous", "washington"}, {"Wash", "ambiguous", "washington"}, {"WI", "ambiguous", "wisconsin"}, {"Wi", "ambiguous", "wisconsin"}, {"WV", "ambiguous", "west", "virginia"}, {"WY", "ambiguous", "wyoming"}, {"Wy", "ambiguous", "wyoming"}, {"Wyo", "", "wyoming"}, {"PR", "ambiguous", "puerto", "rico"}};
        usStatesHash = new Hashtable();

        for(int i = 0; i < usStates.length; ++i) {
            usStatesHash.put(usStates[i][0], usStates[i]);
        }

    }
}

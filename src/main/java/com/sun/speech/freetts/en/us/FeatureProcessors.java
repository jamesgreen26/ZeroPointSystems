//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.sun.speech.freetts.en.us;

import com.sun.speech.freetts.FeatureProcessor;
import com.sun.speech.freetts.Item;
import com.sun.speech.freetts.PartOfSpeech;
import com.sun.speech.freetts.PathExtractor;
import com.sun.speech.freetts.PathExtractorImpl;
import com.sun.speech.freetts.ProcessException;
import com.sun.speech.freetts.Voice;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class FeatureProcessors {
    private static final PathExtractor FIRST_SYLLABLE_PATH = new PathExtractorImpl("R:SylStructure.parent.R:Phrase.parent.daughter.R:SylStructure.daughter", false);
    private static final PathExtractor LAST_SYLLABLE_PATH = new PathExtractorImpl("R:SylStructure.parent.R:Phrase.parent.daughtern.R:SylStructure.daughter", false);
    private static final PathExtractor LAST_LAST_SYLLABLE_PATH = new PathExtractorImpl("R:SylStructure.parent.R:Phrase.parent.daughtern.R:SylStructure.daughtern", false);
    private static final PathExtractor SUB_PHRASE_PATH = new PathExtractorImpl("R:SylStructure.parent.R:Phrase.parent.p", false);
    private static final Pattern DOUBLE_PATTERN;
    private static final Pattern DIGITS_PATTERN;
    private static Set months;
    private static Set days;

    private FeatureProcessors() {
    }

    public static String getPhoneFeature(Item item, String featureName) {
        Voice voice = item.getUtterance().getVoice();
        String feature = voice.getPhoneFeature(item.toString(), featureName);
        return feature;
    }

    public static String wordBreak(Item item) throws ProcessException {
        Item ww = item.getItemAs("Phrase");
        if (ww != null && ww.getNext() == null) {
            String pname = ww.getParent().toString();
            if (pname.equals("BB")) {
                return "4";
            } else {
                return pname.equals("B") ? "3" : "1";
            }
        } else {
            return "1";
        }
    }

    public static String wordPunc(Item item) throws ProcessException {
        Item ww = item.getItemAs("Token");
        if (ww != null && ww.getNext() != null) {
            return "";
        } else {
            return ww != null && ww.getParent() != null ? ww.getParent().getFeatures().getString("punc") : "";
        }
    }

    private static String segCodaCtype(Item seg, String ctype) {
        for(Item daughter = seg.getItemAs("SylStructure").getParent().getLastDaughter(); daughter != null; daughter = daughter.getPrevious()) {
            if ("+".equals(getPhoneFeature(daughter, "vc"))) {
                return "0";
            }

            if (ctype.equals(getPhoneFeature(daughter, "ctype"))) {
                return "1";
            }
        }

        return "0";
    }

    private static String segOnsetCtype(Item seg, String ctype) {
        for(Item daughter = seg.getItemAs("SylStructure").getParent().getDaughter(); daughter != null; daughter = daughter.getNext()) {
            if ("+".equals(getPhoneFeature(daughter, "vc"))) {
                return "0";
            }

            if (ctype.equals(getPhoneFeature(daughter, "ctype"))) {
                return "1";
            }
        }

        return "0";
    }

    private static boolean isAccented(Item item) {
        return item.getFeatures().isPresent("accent") || item.getFeatures().isPresent("endtone");
    }

    private static int rail(int val) {
        return val > 19 ? 19 : val;
    }

    static {
        DOUBLE_PATTERN = Pattern.compile(USEnglish.RX_DOUBLE);
        DIGITS_PATTERN = Pattern.compile(USEnglish.RX_DIGITS);
        months = new HashSet();
        months.add("jan");
        months.add("january");
        months.add("feb");
        months.add("february");
        months.add("mar");
        months.add("march");
        months.add("apr");
        months.add("april");
        months.add("may");
        months.add("jun");
        months.add("june");
        months.add("jul");
        months.add("july");
        months.add("aug");
        months.add("august");
        months.add("sep");
        months.add("september");
        months.add("oct");
        months.add("october");
        months.add("nov");
        months.add("november");
        months.add("dec");
        months.add("december");
        days = new HashSet();
        days.add("sun");
        days.add("sunday");
        days.add("mon");
        days.add("monday");
        days.add("tue");
        days.add("tuesday");
        days.add("wed");
        days.add("wednesday");
        days.add("thu");
        days.add("thursday");
        days.add("fri");
        days.add("friday");
        days.add("sat");
        days.add("saturday");
    }

    public static class Gpos implements FeatureProcessor {
        PartOfSpeech pos;

        public Gpos(PartOfSpeech pos) {
            this.pos = pos;
        }

        public String process(Item item) throws ProcessException {
            return this.pos.getPartOfSpeech(item.toString());
        }
    }

    public static class WordNumSyls implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            int count = 0;

            for(Item daughter = item.getItemAs("SylStructure").getDaughter(); daughter != null; daughter = daughter.getNext()) {
                ++count;
            }

            return Integer.toString(FeatureProcessors.rail(count));
        }
    }

    public static class AccentedSylIn implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            int count = 0;
            Item ss = item.getItemAs("Syllable");
            Item firstSyllable = FeatureProcessors.FIRST_SYLLABLE_PATH.findItem(item);

            for(Item p = ss; p != null; p = p.getPrevious()) {
                if (FeatureProcessors.isAccented(p)) {
                    ++count;
                }

                if (p.equalsShared(firstSyllable)) {
                    break;
                }
            }

            return Integer.toString(FeatureProcessors.rail(count));
        }
    }

    public static class StressedSylIn implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            int count = 0;
            Item ss = item.getItemAs("Syllable");
            Item firstSyllable = FeatureProcessors.FIRST_SYLLABLE_PATH.findItem(item);

            for(Item p = ss.getPrevious(); p != null && !p.equalsShared(firstSyllable); p = p.getPrevious()) {
                if ("1".equals(p.getFeatures().getString("stress"))) {
                    ++count;
                }
            }

            return Integer.toString(FeatureProcessors.rail(count));
        }
    }

    public static class StressedSylOut implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            int count = 0;
            Item ss = item.getItemAs("Syllable");
            Item lastSyllable = FeatureProcessors.LAST_SYLLABLE_PATH.findItem(item);

            for(Item p = ss.getNext(); p != null; p = p.getNext()) {
                if ("1".equals(p.getFeatures().getString("stress"))) {
                    ++count;
                }

                if (p.equalsShared(lastSyllable)) {
                    break;
                }
            }

            return Integer.toString(FeatureProcessors.rail(count));
        }
    }

    public static class NumDigits implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            String name = item.getFeatures().getString("name");
            return Integer.toString(FeatureProcessors.rail(name.length()));
        }
    }

    public static class MonthRange implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            int v = Integer.parseInt(item.getFeatures().getString("name"));
            return v > 0 && v < 32 ? "1" : "0";
        }
    }

    public static class TokenPosGuess implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            String name = item.getFeatures().getString("name");
            String dc = name.toLowerCase();
            if (FeatureProcessors.DIGITS_PATTERN.matcher(dc).matches()) {
                return "numeric";
            } else if (FeatureProcessors.DOUBLE_PATTERN.matcher(dc).matches()) {
                return "number";
            } else if (FeatureProcessors.months.contains(dc)) {
                return "month";
            } else if (FeatureProcessors.days.contains(dc)) {
                return "day";
            } else if (dc.equals("a")) {
                return "a";
            } else if (dc.equals("flight")) {
                return "flight";
            } else {
                return dc.equals("to") ? "to" : "_other_";
            }
        }
    }

    public static class Accented implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            return FeatureProcessors.isAccented(item) ? "1" : "0";
        }
    }

    public static class LastAccent implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            int count = 0;

            for(Item p = item.getItemAs("Syllable"); p != null && !FeatureProcessors.isAccented(p); ++count) {
                p = p.getPrevious();
            }

            return Integer.toString(FeatureProcessors.rail(count));
        }
    }

    public static class PosInSyl implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            int count = -1;

            for(Item p = item.getItemAs("SylStructure"); p != null; p = p.getPrevious()) {
                ++count;
            }

            return Integer.toString(FeatureProcessors.rail(count));
        }
    }

    public static class PositionType implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            Item s = item.getItemAs("SylStructure");
            String type;
            if (s == null) {
                type = "single";
            } else if (s.getNext() == null) {
                if (s.getPrevious() == null) {
                    type = "single";
                } else {
                    type = "final";
                }
            } else if (s.getPrevious() == null) {
                type = "initial";
            } else {
                type = "mid";
            }

            return type;
        }
    }

    public static class SylIn implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            int count = 0;
            Item ss = item.getItemAs("Syllable");
            Item firstSyllable = FeatureProcessors.FIRST_SYLLABLE_PATH.findItem(item);

            for(Item p = ss; p != null && !p.equalsShared(firstSyllable); ++count) {
                p = p.getPrevious();
            }

            return Integer.toString(FeatureProcessors.rail(count));
        }
    }

    public static class SylOut implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            int count = 0;
            Item ss = item.getItemAs("Syllable");
            Item firstSyllable = FeatureProcessors.LAST_LAST_SYLLABLE_PATH.findItem(item);

            for(Item p = ss; p != null && !p.equalsShared(firstSyllable); p = p.getNext()) {
                ++count;
            }

            return Integer.toString(FeatureProcessors.rail(count));
        }
    }

    public static class SylBreak implements FeatureProcessor {
        public String process(Item syl) throws ProcessException {
            Item ss = syl.getItemAs("SylStructure");
            if (ss == null) {
                return "1";
            } else if (ss.getNext() != null) {
                return "0";
            } else {
                return ss.getParent() == null ? "1" : FeatureProcessors.wordBreak(ss.getParent());
            }
        }
    }

    public static class WordBreak implements FeatureProcessor {
        public String process(Item word) throws ProcessException {
            return FeatureProcessors.wordBreak(word);
        }
    }

    public static class WordPunc implements FeatureProcessor {
        public String process(Item word) throws ProcessException {
            return FeatureProcessors.wordPunc(word);
        }
    }

    public static class PH_CPlace implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            return FeatureProcessors.getPhoneFeature(item, "cplace");
        }
    }

    public static class PH_CType implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            return FeatureProcessors.getPhoneFeature(item, "ctype");
        }
    }

    public static class PH_CVox implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            return FeatureProcessors.getPhoneFeature(item, "cvox");
        }
    }

    public static class PH_VC implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            return FeatureProcessors.getPhoneFeature(item, "vc");
        }
    }

    public static class PH_VFront implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            return FeatureProcessors.getPhoneFeature(item, "vfront");
        }
    }

    public static class PH_VHeight implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            return FeatureProcessors.getPhoneFeature(item, "vheight");
        }
    }

    public static class PH_VLength implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            return FeatureProcessors.getPhoneFeature(item, "vlng");
        }
    }

    public static class PH_VRnd implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            return FeatureProcessors.getPhoneFeature(item, "vrnd");
        }
    }

    public static class SylOnsetSize implements FeatureProcessor {
        public String process(Item syl) throws ProcessException {
            int count = 0;

            for(Item daughter = syl.getItemAs("SylStructure").getDaughter(); daughter != null && !"+".equals(FeatureProcessors.getPhoneFeature(daughter, "vc")); daughter = daughter.getNext()) {
                ++count;
            }

            return Integer.toString(FeatureProcessors.rail(count));
        }
    }

    public static class SylCodaSize implements FeatureProcessor {
        public String process(Item syl) throws ProcessException {
            int count = 0;

            for(Item daughter = syl.getItemAs("SylStructure").getLastDaughter(); daughter != null && !"+".equals(FeatureProcessors.getPhoneFeature(daughter, "vc")); ++count) {
                daughter = daughter.getPrevious();
            }

            return Integer.toString(FeatureProcessors.rail(count));
        }
    }

    public static class SegCodaFric implements FeatureProcessor {
        public String process(Item seg) throws ProcessException {
            return FeatureProcessors.segCodaCtype(seg, "f");
        }
    }

    public static class SegOnsetFric implements FeatureProcessor {
        public String process(Item seg) throws ProcessException {
            return FeatureProcessors.segOnsetCtype(seg, "f");
        }
    }

    public static class SegCodaStop implements FeatureProcessor {
        public String process(Item seg) throws ProcessException {
            return FeatureProcessors.segCodaCtype(seg, "s");
        }
    }

    public static class SegOnsetStop implements FeatureProcessor {
        public String process(Item seg) throws ProcessException {
            return FeatureProcessors.segOnsetCtype(seg, "s");
        }
    }

    public static class SegCodaNasal implements FeatureProcessor {
        public String process(Item seg) throws ProcessException {
            return FeatureProcessors.segCodaCtype(seg, "n");
        }
    }

    public static class SegOnsetNasal implements FeatureProcessor {
        public String process(Item seg) throws ProcessException {
            return FeatureProcessors.segOnsetCtype(seg, "n");
        }
    }

    public static class SegCodaGlide implements FeatureProcessor {
        public String process(Item seg) throws ProcessException {
            return FeatureProcessors.segCodaCtype(seg, "r").equals("0") ? FeatureProcessors.segCodaCtype(seg, "l") : "1";
        }
    }

    public static class SegOnsetGlide implements FeatureProcessor {
        public String process(Item seg) throws ProcessException {
            return FeatureProcessors.segOnsetCtype(seg, "r").equals("0") ? FeatureProcessors.segOnsetCtype(seg, "l") : "1";
        }
    }

    public static class SegOnsetCoda implements FeatureProcessor {
        public String process(Item seg) throws ProcessException {
            Item s = seg.getItemAs("SylStructure");
            if (s == null) {
                return "coda";
            } else {
                for(Item var3 = s.getNext(); var3 != null; var3 = var3.getNext()) {
                    if ("+".equals(FeatureProcessors.getPhoneFeature(var3, "vc"))) {
                        return "onset";
                    }
                }

                return "coda";
            }
        }
    }

    public static class SubPhrases implements FeatureProcessor {
        public String process(Item item) throws ProcessException {
            int count = 0;
            Item inPhrase = FeatureProcessors.SUB_PHRASE_PATH.findItem(item);

            for(Item p = inPhrase; p != null; p = p.getPrevious()) {
                ++count;
            }

            return Integer.toString(FeatureProcessors.rail(count));
        }
    }

    public static class SegmentDuration implements FeatureProcessor {
        public String process(Item seg) throws ProcessException {
            if (seg == null) {
                return "0";
            } else {
                return seg.getPrevious() == null ? seg.getFeatures().getObject("end").toString() : Float.toString(seg.getFeatures().getFloat("end") - seg.getPrevious().getFeatures().getFloat("end"));
            }
        }
    }
}

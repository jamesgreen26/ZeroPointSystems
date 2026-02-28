//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.sun.speech.freetts.en.us;

import com.sun.speech.freetts.VoiceManager;
import com.sun.speech.freetts.lexicon.LexiconImpl;
import com.sun.speech.freetts.util.BulkTimer;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

public class CMULexicon extends LexiconImpl {
    private static final String VOWELS = "aeiou";
    private static final String GLIDES_LIQUIDS = "wylr";
    private static final String NASALS = "nm";
    private static final String VOICED_OBSTRUENTS = "bdgjlmnnnrvwyz";

    public CMULexicon(URL compiledURL, URL addendaURL, URL letterToSoundURL, boolean binary) {
        this.setLexiconParameters(compiledURL, addendaURL, letterToSoundURL, binary);
    }

    public CMULexicon() {
        this("cmulex");
    }

    public CMULexicon(String basename) {
        this(basename, true);
    }

    public CMULexicon(String basename, boolean useBinaryIO) {
        URLClassLoader classLoader = VoiceManager.getVoiceClassLoader();
        String type = useBinaryIO ? "bin" : "txt";
        URL letterToSoundURL = classLoader.getResource("com/sun/speech/freetts/en/us/" + basename + "_lts." + type);
        URL compiledURL = classLoader.getResource("com/sun/speech/freetts/en/us/" + basename + "_compiled." + type);
        URL addendaURL = classLoader.getResource("com/sun/speech/freetts/en/us/" + basename + "_addenda." + type);
        if (letterToSoundURL == null) {
            Class cls = CMULexicon.class;
            letterToSoundURL = cls.getResource(basename + "_lts." + type);
            compiledURL = cls.getResource(basename + "_compiled." + type);
            addendaURL = cls.getResource(basename + "_addenda." + type);
            if (letterToSoundURL == null) {
                System.err.println("CMULexicon: Oh no!  Couldn't find lexicon data!");
            }
        }

        this.setLexiconParameters(compiledURL, addendaURL, letterToSoundURL, useBinaryIO);
    }

    public static CMULexicon getInstance(boolean useBinaryIO) throws IOException {
        return getInstance("cmulex", useBinaryIO);
    }

    public static CMULexicon getInstance(String basename, boolean useBinaryIO) throws IOException {
        CMULexicon lexicon = new CMULexicon(basename, useBinaryIO);
        lexicon.load();
        return lexicon;
    }

    public boolean isSyllableBoundary(List syllablePhones, String[] wordPhones, int currentWordPhone) {
        if (currentWordPhone >= wordPhones.length) {
            return true;
        } else if (isSilence(wordPhones[currentWordPhone])) {
            return true;
        } else if (!hasVowel(wordPhones, currentWordPhone)) {
            return false;
        } else if (!hasVowel(syllablePhones)) {
            return false;
        } else if (isVowel(wordPhones[currentWordPhone])) {
            return true;
        } else if (currentWordPhone == wordPhones.length - 1) {
            return false;
        } else {
            int p = getSonority((String)syllablePhones.get(syllablePhones.size() - 1));
            int n = getSonority(wordPhones[currentWordPhone]);
            int nn = getSonority(wordPhones[currentWordPhone + 1]);
            return p <= n && n <= nn;
        }
    }

    protected static boolean isSilence(String phone) {
        return phone.equals("pau");
    }

    protected static boolean hasVowel(String[] phones, int index) {
        for(int i = index; i < phones.length; ++i) {
            if (isVowel(phones[i])) {
                return true;
            }
        }

        return false;
    }

    protected static boolean hasVowel(List phones) {
        for(int i = 0; i < phones.size(); ++i) {
            if (isVowel((String)phones.get(i))) {
                return true;
            }
        }

        return false;
    }

    protected static boolean isVowel(String phone) {
        return "aeiou".indexOf(phone.substring(0, 1)) != -1;
    }

    protected static int getSonority(String phone) {
        if (!isVowel(phone) && !isSilence(phone)) {
            if ("wylr".indexOf(phone.substring(0, 1)) != -1) {
                return 4;
            } else if ("nm".indexOf(phone.substring(0, 1)) != -1) {
                return 3;
            } else {
                return "bdgjlmnnnrvwyz".indexOf(phone.substring(0, 1)) != -1 ? 2 : 1;
            }
        } else {
            return 5;
        }
    }

    public static void main(String[] args) {
        boolean showTimes = false;
        String srcPath = ".";
        String destPath = ".";
        String baseName = "cmulex";

        try {
            if (args.length > 0) {
                BulkTimer.LOAD.start();

                for(int i = 0; i < args.length; ++i) {
                    if (args[i].equals("-src")) {
                        ++i;
                        srcPath = args[i];
                    } else if (args[i].equals("-dest")) {
                        ++i;
                        destPath = args[i];
                    } else if (args[i].equals("-name") && i < args.length - 1) {
                        ++i;
                        baseName = args[i];
                    } else if (args[i].equals("-generate_binary")) {
                        System.out.println("Loading " + baseName);
                        String path = "file:" + srcPath + "/" + baseName;
                        LexiconImpl lex = new CMULexicon(new URL(path + "_compiled.txt"), new URL(path + "_addenda.txt"), new URL(path + "_lts.txt"), false);
                        BulkTimer.LOAD.start("load_text");
                        lex.load();
                        BulkTimer.LOAD.stop("load_text");
                        System.out.println("Dumping " + baseName);
                        BulkTimer.LOAD.start("dump_text");
                        lex.dumpBinary(destPath + "/" + baseName);
                        BulkTimer.LOAD.stop("dump_text");
                    } else if (args[i].equals("-compare")) {
                        BulkTimer.LOAD.start("load_text");
                        LexiconImpl lex = getInstance(baseName, false);
                        BulkTimer.LOAD.stop("load_text");
                        BulkTimer.LOAD.start("load_binary");
                        LexiconImpl lex2 = getInstance(baseName, true);
                        BulkTimer.LOAD.stop("load_binary");
                        BulkTimer.LOAD.start("compare");
                        lex.compare(lex2);
                        BulkTimer.LOAD.stop("compare");
                    } else if (args[i].equals("-showtimes")) {
                        showTimes = true;
                    } else {
                        System.out.println("Unknown option " + args[i]);
                    }
                }

                BulkTimer.LOAD.stop();
                if (showTimes) {
                    BulkTimer.LOAD.show("CMULexicon loading and dumping");
                }
            } else {
                System.out.println("Options: ");
                System.out.println("    -src path");
                System.out.println("    -dest path");
                System.out.println("    -compare");
                System.out.println("    -generate_binary");
                System.out.println("    -showtimes");
            }
        } catch (IOException ioe) {
            System.err.println(ioe);
        }

    }
}

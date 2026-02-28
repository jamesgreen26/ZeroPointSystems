//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.sun.speech.freetts.en.us.cmu_us_kal;

import com.sun.speech.freetts.Age;
import com.sun.speech.freetts.Gender;
import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceDirectory;
import com.sun.speech.freetts.en.us.CMUDiphoneVoice;
import com.sun.speech.freetts.en.us.CMULexicon;
import java.util.Locale;

public class KevinVoiceDirectory extends VoiceDirectory {
    public Voice[] getVoices() {
        CMULexicon lexicon = new CMULexicon("cmulex");
        Voice kevin = new CMUDiphoneVoice("kevin", Gender.MALE, Age.YOUNGER_ADULT, "default 8-bit diphone voice", Locale.US, "general", "cmu", lexicon, this.getClass().getResource("cmu_us_kal.bin"));
        Voice kevin16 = new CMUDiphoneVoice("kevin16", Gender.MALE, Age.YOUNGER_ADULT, "default 16-bit diphone voice", Locale.US, "general", "cmu", lexicon, this.getClass().getResource("cmu_us_kal16.bin"));
        Voice[] voices = new Voice[]{kevin, kevin16};
        return voices;
    }

    public static void main(String[] args) {
        System.out.println((new KevinVoiceDirectory()).toString());
    }
}

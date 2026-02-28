//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.sun.speech.freetts.en.us;

import com.sun.speech.freetts.Age;
import com.sun.speech.freetts.Gender;
import com.sun.speech.freetts.UtteranceProcessor;
import com.sun.speech.freetts.diphone.DiphonePitchmarkGenerator;
import com.sun.speech.freetts.diphone.DiphoneUnitSelector;
import com.sun.speech.freetts.relp.UnitConcatenator;
import de.dfki.lt.freetts.ConcatenativeVoice;
import java.io.IOException;
import java.net.URL;
import java.util.Locale;

public class CMUDiphoneVoice extends CMUVoice implements ConcatenativeVoice {
    protected URL database;

    public CMUDiphoneVoice() {
        this((String)null, (Gender)null, (Age)null, (String)null, (Locale)null, (String)null, (String)null, (CMULexicon)null, (URL)null);
    }

    public CMUDiphoneVoice(String name, Gender gender, Age age, String description, Locale locale, String domain, String organization, CMULexicon lexicon, URL database) {
        super(name, gender, age, description, locale, domain, organization, lexicon);
        this.setRate(150.0F);
        this.setPitch(100.0F);
        this.setPitchRange(11.0F);
        this.database = database;
    }

    public URL getDatabase() {
        if (this.database == null) {
            String name = this.getFeatures().getString("databaseName");
            this.database = this.getClass().getResource(name);
        }

        return this.database;
    }

    protected void setupFeatureSet() throws IOException {
        super.setupFeatureSet();
    }

    protected UtteranceProcessor getPostLexicalAnalyzer() throws IOException {
        return new CMUDiphoneVoicePostLexicalAnalyzer();
    }

    public UtteranceProcessor getPitchmarkGenerator() throws IOException {
        return new DiphonePitchmarkGenerator();
    }

    public UtteranceProcessor getUnitConcatenator() throws IOException {
        return new UnitConcatenator();
    }

    public UtteranceProcessor getUnitSelector() throws IOException {
        return new DiphoneUnitSelector(this.getDatabase());
    }

    public String toString() {
        return "CMUDiphoneVoice";
    }
}

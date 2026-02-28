//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.sun.speech.freetts.en.us;

import com.sun.speech.freetts.Age;
import com.sun.speech.freetts.Gender;
import com.sun.speech.freetts.UtteranceProcessor;
import com.sun.speech.freetts.clunits.ClusterUnitPitchmarkGenerator;
import com.sun.speech.freetts.clunits.ClusterUnitSelector;
import com.sun.speech.freetts.relp.UnitConcatenator;
import de.dfki.lt.freetts.ConcatenativeVoice;
import java.io.IOException;
import java.net.URL;
import java.util.Locale;

public class CMUClusterUnitVoice extends CMUVoice implements ConcatenativeVoice {
    protected URL database;

    public CMUClusterUnitVoice(String name, Gender gender, Age age, String description, Locale locale, String domain, String organization, CMULexicon lexicon, URL database) {
        super(name, gender, age, description, locale, domain, organization, lexicon);
        this.setRate(150.0F);
        this.setPitch(100.0F);
        this.setPitchRange(12.0F);
        this.database = database;
    }

    public URL getDatabase() {
        return this.database;
    }

    protected void setupFeatureSet() throws IOException {
        super.setupFeatureSet();
        this.getFeatures().setString("join_type", "simple_join");
    }

    public UtteranceProcessor getUnitSelector() throws IOException {
        return new ClusterUnitSelector(this.getDatabase());
    }

    public UtteranceProcessor getPitchmarkGenerator() throws IOException {
        return new ClusterUnitPitchmarkGenerator();
    }

    public UtteranceProcessor getUnitConcatenator() throws IOException {
        return new UnitConcatenator();
    }

    public String toString() {
        return "CMUClusterUnitVoice";
    }
}

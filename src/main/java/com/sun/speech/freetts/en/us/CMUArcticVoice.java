//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.sun.speech.freetts.en.us;

import com.sun.speech.freetts.Age;
import com.sun.speech.freetts.Gender;
import com.sun.speech.freetts.Item;
import com.sun.speech.freetts.UtteranceProcessor;
import com.sun.speech.freetts.clunits.ClusterUnitSelector;
import de.dfki.lt.freetts.ClusterUnitNamer;
import java.io.IOException;
import java.net.URL;
import java.util.Locale;

public class CMUArcticVoice extends CMUClusterUnitVoice {
    public CMUArcticVoice(String name, Gender gender, Age age, String description, Locale locale, String domain, String organization, CMULexicon lexicon, URL database) {
        super(name, gender, age, description, locale, domain, organization, lexicon, database);
    }

    public UtteranceProcessor getUnitSelector() throws IOException {
        ClusterUnitNamer unitNamer = new ClusterUnitNamer() {
            public void setUnitName(Item seg) {
                String VOWELS = "aeiou";
                String cname = null;
                String segName = seg.getFeatures().getString("name");
                if (segName.equals("pau")) {
                    cname = segName;
                } else if (VOWELS.indexOf(segName.charAt(0)) >= 0) {
                    cname = segName + seg.findFeature("R:SylStructure.parent.stress");
                } else {
                    cname = segName + seg.findFeature("seg_onsetcoda");
                }

                seg.getFeatures().setString("clunit_name", cname);
            }
        };
        return new ClusterUnitSelector(this.getDatabase(), unitNamer);
    }
}

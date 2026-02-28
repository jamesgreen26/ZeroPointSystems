//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.sun.speech.freetts.en;

import com.sun.speech.freetts.Item;
import com.sun.speech.freetts.PathExtractor;
import com.sun.speech.freetts.PathExtractorImpl;
import com.sun.speech.freetts.ProcessException;
import com.sun.speech.freetts.Relation;
import com.sun.speech.freetts.Utterance;
import com.sun.speech.freetts.UtteranceProcessor;
import com.sun.speech.freetts.Voice;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

public class ContourGenerator implements UtteranceProcessor {
    private static final PathExtractor endPath = new PathExtractorImpl("R:SylStructure.daughter.R:Segment.p.end", true);
    private static final PathExtractor lastDaughterEndPath = new PathExtractorImpl("R:SylStructure.daughtern.end", true);
    private static final PathExtractor postBreakPath = new PathExtractorImpl("R:SylStructure.daughter.R:Segment.p.name", true);
    private static final PathExtractor preBreakPath = new PathExtractorImpl("R:SylStructure.daughtern.R:Segment.n.name", true);
    private static final PathExtractor vowelMidPath = new PathExtractorImpl("R:Segment.p.end", true);
    private static final PathExtractor localF0Shift = new PathExtractorImpl("R:SylStructure.parent.R:Token.parent.local_f0_shift", true);
    private static final PathExtractor localF0Range = new PathExtractorImpl("R:SylStructure.parent.R:Token.parent.local_f0_range", true);
    private final float modelMean;
    private final float modelStddev;
    private F0ModelTerm[] terms = new F0ModelTerm[]{null};

    public ContourGenerator(URL url, float modelMean, float modelStddev) throws IOException {
        this.modelMean = modelMean;
        this.modelStddev = modelStddev;
        List termsList = new ArrayList();
        BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));

        for(String line = reader.readLine(); line != null; line = reader.readLine()) {
            if (!line.startsWith("***")) {
                this.parseAndAdd(termsList, line);
            }
        }

        this.terms = (F0ModelTerm[])termsList.toArray(this.terms);
        reader.close();
    }

    public void processUtterance(Utterance utterance) throws ProcessException {
        float lend = 0.0F;
        float mean = utterance.getVoice().getPitch();
        mean *= utterance.getVoice().getPitchShift();
        float stddev = utterance.getVoice().getPitchRange();
        Relation target = utterance.createRelation("Target");

        for(Item syllable = utterance.getRelation("Syllable").getHead(); syllable != null; syllable = syllable.getNext()) {
            if (syllable.getItemAs("SylStructure").hasDaughters()) {
                Object tval = localF0Shift.findFeature(syllable);
                float localMean = Float.parseFloat(tval.toString());
                if ((double)localMean == (double)0.0F) {
                    localMean = mean;
                } else {
                    localMean *= mean;
                }

                tval = localF0Range.findFeature(syllable);
                float localStddev = Float.parseFloat(tval.toString());
                if ((double)localStddev == (double)0.0F) {
                    localStddev = stddev;
                }

                Interceptor interceptor = this.applyLrModel(syllable);
                if (this.isPostBreak(syllable)) {
                    lend = this.mapF0(interceptor.start, localMean, localStddev);
                }

                Float val = (Float)endPath.findFeature(syllable);
                this.addTargetPoint(target, val, this.mapF0((interceptor.start + lend) / 2.0F, localMean, localStddev));
                this.addTargetPoint(target, this.vowelMid(syllable), this.mapF0(interceptor.mid, localMean, localStddev));
                lend = this.mapF0(interceptor.end, localMean, localStddev);
                if (this.isPreBreak(syllable)) {
                    Float eval = (Float)lastDaughterEndPath.findFeature(syllable);
                    this.addTargetPoint(target, eval, this.mapF0(interceptor.end, localMean, localStddev));
                }
            }
        }

        if (utterance.getRelation("Segment").getHead() != null) {
            Item first = target.getHead();
            if (first == null) {
                this.addTargetPoint(target, 0.0F, mean);
            } else if (first.getFeatures().getFloat("pos") > 0.0F) {
                Item newItem = first.prependItem((Item)null);
                newItem.getFeatures().setFloat("pos", 0.0F);
                newItem.getFeatures().setFloat("f0", first.getFeatures().getFloat("f0"));
            }

            Item last = target.getTail();
            Item lastSegment = utterance.getRelation("Segment").getTail();
            float segEnd = 0.0F;
            if (lastSegment != null) {
                segEnd = lastSegment.getFeatures().getFloat("end");
            }

            if (last.getFeatures().getFloat("pos") < segEnd) {
                this.addTargetPoint(target, segEnd, last.getFeatures().getFloat("f0"));
            }
        }

    }

    private Interceptor applyLrModel(Item syllable) {
        float fv = 0.0F;
        Interceptor interceptor = new Interceptor();
        interceptor.start = this.terms[0].start;
        interceptor.mid = this.terms[0].mid;
        interceptor.end = this.terms[0].end;

        for(int i = 1; i < this.terms.length; ++i) {
            Object value = this.terms[i].findFeature(syllable);
            if (this.terms[i].type != null) {
                if (value.toString().equals(this.terms[i].type)) {
                    fv = 1.0F;
                } else {
                    fv = 0.0F;
                }
            } else {
                fv = Float.parseFloat(value.toString());
            }

            interceptor.start += fv * this.terms[i].start;
            interceptor.mid += fv * this.terms[i].mid;
            interceptor.end += fv * this.terms[i].end;
        }

        return interceptor;
    }

    private final float vowelMid(Item syllable) {
        Voice voice = syllable.getUtterance().getVoice();
        Item firstSeg = syllable.getItemAs("SylStructure").getDaughter();

        for(Item segment = firstSeg; segment != null; segment = segment.getNext()) {
            if ("+".equals(voice.getPhoneFeature(segment.toString(), "vc"))) {
                float val = (segment.getFeatures().getFloat("end") + (Float)vowelMidPath.findFeature(segment)) / 2.0F;
                return val;
            }
        }

        float val;
        if (firstSeg == null) {
            val = 0.0F;
        } else {
            val = (firstSeg.getFeatures().getFloat("end") + (Float)vowelMidPath.findFeature(firstSeg)) / 2.0F;
        }

        return val;
    }

    private void addTargetPoint(Relation target, float pos, float f0) {
        Item item = target.appendItem();
        item.getFeatures().setFloat("pos", pos);
        if ((double)f0 > (double)500.0F) {
            item.getFeatures().setFloat("f0", 500.0F);
        } else if ((double)f0 < (double)50.0F) {
            item.getFeatures().setFloat("f0", 50.0F);
        } else {
            item.getFeatures().setFloat("f0", f0);
        }

    }

    private final boolean isPostBreak(Item syllable) {
        return syllable.getPrevious() == null || "pau".equals(postBreakPath.findFeature(syllable));
    }

    private final boolean isPreBreak(Item syllable) {
        return syllable.getNext() == null || "pau".equals(preBreakPath.findFeature(syllable));
    }

    private final float mapF0(float val, float mean, float stddev) {
        return (val - this.modelMean) / this.modelStddev * stddev + mean;
    }

    protected void parseAndAdd(List list, String line) {
        try {
            StringTokenizer tokenizer = new StringTokenizer(line, " ");
            String feature = tokenizer.nextToken();
            float start = Float.parseFloat(tokenizer.nextToken());
            float mid = Float.parseFloat(tokenizer.nextToken());
            float end = Float.parseFloat(tokenizer.nextToken());
            String type = tokenizer.nextToken();
            if (type.equals("null")) {
                type = null;
            }

            list.add(new F0ModelTerm(feature, start, mid, end, type));
        } catch (NoSuchElementException nsee) {
            throw new Error("ContourGenerator: Error while parsing F0ModelTerm " + nsee.getMessage());
        } catch (NumberFormatException nfe) {
            throw new Error("ContourGenerator: Bad float format " + nfe.getMessage());
        }
    }

    public String toString() {
        return "ContourGenerator";
    }
}

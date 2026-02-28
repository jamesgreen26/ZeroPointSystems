//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.sun.speech.freetts.en.us;

import com.sun.speech.freetts.Age;
import com.sun.speech.freetts.FeatureSet;
import com.sun.speech.freetts.Gender;
import com.sun.speech.freetts.PartOfSpeech;
import com.sun.speech.freetts.PartOfSpeechImpl;
import com.sun.speech.freetts.PhoneDurations;
import com.sun.speech.freetts.PhoneDurationsImpl;
import com.sun.speech.freetts.PhoneSet;
import com.sun.speech.freetts.PhoneSetImpl;
import com.sun.speech.freetts.Segmenter;
import com.sun.speech.freetts.Tokenizer;
import com.sun.speech.freetts.UtteranceProcessor;
import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.cart.CARTImpl;
import com.sun.speech.freetts.cart.Durator;
import com.sun.speech.freetts.cart.Intonator;
import com.sun.speech.freetts.cart.Phraser;
import com.sun.speech.freetts.en.ContourGenerator;
import com.sun.speech.freetts.en.PartOfSpeechTagger;
import com.sun.speech.freetts.en.PauseGenerator;
import com.sun.speech.freetts.en.PostLexicalAnalyzer;
import com.sun.speech.freetts.en.TokenizerImpl;
import com.sun.speech.freetts.relp.AudioOutput;
import com.sun.speech.freetts.util.BulkTimer;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public abstract class CMUVoice extends Voice {
    private PhoneSet phoneSet;

    public CMUVoice(String name, Gender gender, Age age, String description, Locale locale, String domain, String organization, CMULexicon lexicon) {
        super(name, gender, age, description, locale, domain, organization);
        this.setLexicon(lexicon);
    }

    protected void loader() throws IOException {
        this.setupFeatureSet();
        this.setupUtteranceProcessors();
        this.setupFeatureProcessors();
    }

    protected void setupFeatureSet() throws IOException {
        BulkTimer.LOAD.start("FeatureSet");
        FeatureSet features = this.getFeatures();
        features.setString("silence", "pau");
        features.setString("join_type", "simple_join");
        BulkTimer.LOAD.stop("FeatureSet");
    }

    protected void setupUtteranceProcessors() throws IOException {
        List processors = this.getUtteranceProcessors();
        BulkTimer.LOAD.start("CartLoading");
        CARTImpl numbersCart = new CARTImpl(this.getResource("nums_cart.txt"));
        CARTImpl phrasingCart = new CARTImpl(this.getResource("phrasing_cart.txt"));
        CARTImpl accentCart = new CARTImpl(this.getResource("int_accent_cart.txt"));
        CARTImpl toneCart = new CARTImpl(this.getResource("int_tone_cart.txt"));
        CARTImpl durzCart = new CARTImpl(this.getResource("durz_cart.txt"));
        BulkTimer.LOAD.stop("CartLoading");
        BulkTimer.LOAD.start("UtteranceProcessors");
        PhoneDurations phoneDurations = new PhoneDurationsImpl(this.getResource("dur_stat.txt"));
        PronounceableFSM prefixFSM = new PrefixFSM(this.getResource("prefix_fsm.txt"));
        PronounceableFSM suffixFSM = new SuffixFSM(this.getResource("suffix_fsm.txt"));
        processors.add(new TokenToWords(numbersCart, prefixFSM, suffixFSM));
        processors.add(new PartOfSpeechTagger());
        processors.add(new Phraser(phrasingCart));
        processors.add(new Segmenter());
        processors.add(new PauseGenerator());
        processors.add(new Intonator(accentCart, toneCart));
        processors.add(this.getPostLexicalAnalyzer());
        processors.add(new Durator(durzCart, 150.0F, phoneDurations));
        processors.add(new ContourGenerator(this.getResource("f0_lr_terms.txt"), 170.0F, 34.0F));
        processors.add(this.getUnitSelector());
        processors.add(this.getPitchmarkGenerator());
        processors.add(this.getUnitConcatenator());
        BulkTimer.LOAD.stop("UtteranceProcessors");
    }

    protected UtteranceProcessor getPostLexicalAnalyzer() throws IOException {
        return new PostLexicalAnalyzer();
    }

    protected UtteranceProcessor getUnitSelector() throws IOException {
        return null;
    }

    protected UtteranceProcessor getPitchmarkGenerator() throws IOException {
        return null;
    }

    protected UtteranceProcessor getUnitConcatenator() throws IOException {
        return null;
    }

    protected void setupFeatureProcessors() throws IOException {
        BulkTimer.LOAD.start("FeatureProcessing");
        PartOfSpeech pos = new PartOfSpeechImpl(this.getResource("part_of_speech.txt"), "content");
        this.phoneSet = new PhoneSetImpl(this.getResource("phoneset.txt"));
        this.addFeatureProcessor("word_break", new FeatureProcessors.WordBreak());
        this.addFeatureProcessor("word_punc", new FeatureProcessors.WordPunc());
        this.addFeatureProcessor("gpos", new FeatureProcessors.Gpos(pos));
        this.addFeatureProcessor("word_numsyls", new FeatureProcessors.WordNumSyls());
        this.addFeatureProcessor("ssyl_in", new FeatureProcessors.StressedSylIn());
        this.addFeatureProcessor("syl_in", new FeatureProcessors.SylIn());
        this.addFeatureProcessor("syl_out", new FeatureProcessors.SylOut());
        this.addFeatureProcessor("ssyl_out", new FeatureProcessors.StressedSylOut());
        this.addFeatureProcessor("syl_break", new FeatureProcessors.SylBreak());
        this.addFeatureProcessor("old_syl_break", new FeatureProcessors.SylBreak());
        this.addFeatureProcessor("num_digits", new FeatureProcessors.NumDigits());
        this.addFeatureProcessor("month_range", new FeatureProcessors.MonthRange());
        this.addFeatureProcessor("token_pos_guess", new FeatureProcessors.TokenPosGuess());
        this.addFeatureProcessor("segment_duration", new FeatureProcessors.SegmentDuration());
        this.addFeatureProcessor("sub_phrases", new FeatureProcessors.SubPhrases());
        this.addFeatureProcessor("asyl_in", new FeatureProcessors.AccentedSylIn());
        this.addFeatureProcessor("last_accent", new FeatureProcessors.LastAccent());
        this.addFeatureProcessor("pos_in_syl", new FeatureProcessors.PosInSyl());
        this.addFeatureProcessor("position_type", new FeatureProcessors.PositionType());
        this.addFeatureProcessor("ph_cplace", new FeatureProcessors.PH_CPlace());
        this.addFeatureProcessor("ph_ctype", new FeatureProcessors.PH_CType());
        this.addFeatureProcessor("ph_cvox", new FeatureProcessors.PH_CVox());
        this.addFeatureProcessor("ph_vc", new FeatureProcessors.PH_VC());
        this.addFeatureProcessor("ph_vfront", new FeatureProcessors.PH_VFront());
        this.addFeatureProcessor("ph_vheight", new FeatureProcessors.PH_VHeight());
        this.addFeatureProcessor("ph_vlng", new FeatureProcessors.PH_VLength());
        this.addFeatureProcessor("ph_vrnd", new FeatureProcessors.PH_VRnd());
        this.addFeatureProcessor("seg_coda_fric", new FeatureProcessors.SegCodaFric());
        this.addFeatureProcessor("seg_onset_fric", new FeatureProcessors.SegOnsetFric());
        this.addFeatureProcessor("seg_coda_stop", new FeatureProcessors.SegCodaStop());
        this.addFeatureProcessor("seg_onset_stop", new FeatureProcessors.SegOnsetStop());
        this.addFeatureProcessor("seg_coda_nasal", new FeatureProcessors.SegCodaNasal());
        this.addFeatureProcessor("seg_onset_nasal", new FeatureProcessors.SegOnsetNasal());
        this.addFeatureProcessor("seg_coda_glide", new FeatureProcessors.SegCodaGlide());
        this.addFeatureProcessor("seg_onset_glide", new FeatureProcessors.SegOnsetGlide());
        this.addFeatureProcessor("seg_onsetcoda", new FeatureProcessors.SegOnsetCoda());
        this.addFeatureProcessor("syl_codasize", new FeatureProcessors.SylCodaSize());
        this.addFeatureProcessor("syl_onsetsize", new FeatureProcessors.SylOnsetSize());
        this.addFeatureProcessor("accented", new FeatureProcessors.Accented());
        BulkTimer.LOAD.stop("FeatureProcessing");
    }

    public String getPhoneFeature(String phone, String featureName) {
        return this.phoneSet.getPhoneFeature(phone, featureName);
    }

    protected UtteranceProcessor getAudioOutput() throws IOException {
        return new AudioOutput();
    }

    public Tokenizer getTokenizer() {
        Tokenizer tokenizer = new TokenizerImpl();
        tokenizer.setWhitespaceSymbols(" \t\n\r");
        tokenizer.setSingleCharSymbols("");
        tokenizer.setPrepunctuationSymbols("\"'`({[");
        tokenizer.setPostpunctuationSymbols("\"'`.,:;!?(){}[]");
        return tokenizer;
    }

    public String toString() {
        return "CMUVoice";
    }
}

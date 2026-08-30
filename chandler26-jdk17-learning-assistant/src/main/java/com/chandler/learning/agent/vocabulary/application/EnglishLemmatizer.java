package com.chandler.learning.agent.vocabulary.application;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 英语词形还原器，提供基于规则和常见不规则形态映射的词根推导能力，
 * 支持名词复数、动词时态（过去式/分词/进行时/第三人称单数）、比较级/最高级还原。
 */
@Component
public class EnglishLemmatizer {

    private static final Map<String, String> IRREGULAR_MAP = Map.ofEntries(
            // 不规则动词
            Map.entry("went", "go"), Map.entry("gone", "go"), Map.entry("goes", "go"), Map.entry("going", "go"),
            Map.entry("ran", "run"), Map.entry("running", "run"), Map.entry("runs", "run"),
            Map.entry("saw", "see"), Map.entry("seen", "see"), Map.entry("sees", "see"), Map.entry("seeing", "see"),
            Map.entry("did", "do"), Map.entry("done", "do"), Map.entry("does", "do"), Map.entry("doing", "do"),
            Map.entry("had", "have"), Map.entry("has", "have"), Map.entry("having", "have"),
            Map.entry("was", "be"), Map.entry("were", "be"), Map.entry("been", "be"), Map.entry("being", "be"),
            Map.entry("am", "be"), Map.entry("is", "be"), Map.entry("are", "be"),
            Map.entry("bought", "buy"), Map.entry("buying", "buy"), Map.entry("buys", "buy"),
            Map.entry("brought", "bring"), Map.entry("bringing", "bring"), Map.entry("brings", "bring"),
            Map.entry("caught", "catch"), Map.entry("catching", "catch"), Map.entry("catches", "catch"),
            Map.entry("taught", "teach"), Map.entry("teaching", "teach"), Map.entry("teaches", "teach"),
            Map.entry("thought", "think"), Map.entry("thinking", "think"), Map.entry("thinks", "think"),
            Map.entry("slept", "sleep"), Map.entry("sleeping", "sleep"), Map.entry("sleeps", "sleep"),
            Map.entry("kept", "keep"), Map.entry("keeping", "keep"), Map.entry("keeps", "keep"),
            Map.entry("left", "leave"), Map.entry("leaving", "leave"), Map.entry("leaves", "leave"),
            Map.entry("felt", "feel"), Map.entry("feeling", "feel"), Map.entry("feels", "feel"),
            Map.entry("built", "build"), Map.entry("building", "build"), Map.entry("builds", "build"),
            Map.entry("spent", "spend"), Map.entry("spending", "spend"), Map.entry("spends", "spend"),
            Map.entry("sent", "send"), Map.entry("sending", "send"), Map.entry("sends", "send"),
            Map.entry("lent", "lend"), Map.entry("lending", "lend"), Map.entry("lends", "lend"),
            Map.entry("meant", "mean"), Map.entry("meaning", "mean"), Map.entry("means", "mean"),
            Map.entry("met", "meet"), Map.entry("meeting", "meet"), Map.entry("meets", "meet"),
            Map.entry("sat", "sit"), Map.entry("sitting", "sit"), Map.entry("sits", "sit"),
            Map.entry("stood", "stand"), Map.entry("standing", "stand"), Map.entry("stands", "stand"),
            Map.entry("understood", "understand"), Map.entry("understanding", "understand"), Map.entry("understands", "understand"),
            Map.entry("took", "take"), Map.entry("taken", "take"), Map.entry("taking", "take"), Map.entry("takes", "take"),
            Map.entry("gave", "give"), Map.entry("given", "give"), Map.entry("giving", "give"), Map.entry("gives", "give"),
            Map.entry("wrote", "write"), Map.entry("written", "write"), Map.entry("writing", "write"), Map.entry("writes", "write"),
            Map.entry("spoke", "speak"), Map.entry("spoken", "speak"), Map.entry("speaking", "speak"), Map.entry("speaks", "speak"),
            Map.entry("broke", "break"), Map.entry("broken", "break"), Map.entry("breaking", "break"), Map.entry("breaks", "break"),
            Map.entry("chose", "choose"), Map.entry("chosen", "choose"), Map.entry("choosing", "choose"), Map.entry("chooses", "choose"),
            Map.entry("drove", "drive"), Map.entry("driven", "drive"), Map.entry("driving", "drive"), Map.entry("drives", "drive"),
            Map.entry("flew", "fly"), Map.entry("flown", "fly"), Map.entry("flying", "fly"), Map.entry("flies", "fly"),
            Map.entry("drew", "draw"), Map.entry("drawn", "draw"), Map.entry("drawing", "draw"), Map.entry("draws", "draw"),
            Map.entry("swam", "swim"), Map.entry("swum", "swim"), Map.entry("swimming", "swim"), Map.entry("swims", "swim"),
            Map.entry("ate", "eat"), Map.entry("eaten", "eat"), Map.entry("eating", "eat"), Map.entry("eats", "eat"),
            Map.entry("fell", "fall"), Map.entry("fallen", "fall"), Map.entry("falling", "fall"), Map.entry("falls", "fall"),
            Map.entry("forgot", "forget"), Map.entry("forgotten", "forget"), Map.entry("forgetting", "forget"), Map.entry("forgets", "forget"),
            Map.entry("got", "get"), Map.entry("gotten", "get"), Map.entry("getting", "get"), Map.entry("gets", "get"),
            Map.entry("knew", "know"), Map.entry("known", "know"), Map.entry("knowing", "know"), Map.entry("knows", "know"),
            Map.entry("grew", "grow"), Map.entry("grown", "grow"), Map.entry("growing", "grow"), Map.entry("grows", "grow"),
            Map.entry("threw", "throw"), Map.entry("thrown", "throw"), Map.entry("throwing", "throw"), Map.entry("throws", "throw"),
            Map.entry("blew", "blow"), Map.entry("blown", "blow"), Map.entry("blowing", "blow"), Map.entry("blows", "blow"),
            Map.entry("wore", "wear"), Map.entry("worn", "wear"), Map.entry("wearing", "wear"), Map.entry("wears", "wear"),
            Map.entry("tore", "tear"), Map.entry("torn", "tear"), Map.entry("tearing", "tear"), Map.entry("tears", "tear"),
            Map.entry("hid", "hide"), Map.entry("hidden", "hide"), Map.entry("hiding", "hide"), Map.entry("hides", "hide"),
            Map.entry("bit", "bite"), Map.entry("bitten", "bite"), Map.entry("biting", "bite"), Map.entry("bites", "bite"),
            Map.entry("lost", "lose"), Map.entry("losing", "lose"), Map.entry("loses", "lose"),
            Map.entry("found", "find"), Map.entry("finding", "find"), Map.entry("finds", "find"),
            Map.entry("heard", "hear"), Map.entry("hearing", "hear"), Map.entry("hears", "hear"),
            Map.entry("paid", "pay"), Map.entry("paying", "pay"), Map.entry("pays", "pay"),
            Map.entry("said", "say"), Map.entry("saying", "say"), Map.entry("says", "say"),
            Map.entry("sold", "sell"), Map.entry("selling", "sell"), Map.entry("sells", "sell"),
            Map.entry("told", "tell"), Map.entry("telling", "tell"), Map.entry("tells", "tell"),
            Map.entry("won", "win"), Map.entry("winning", "win"), Map.entry("wins", "win"),
            Map.entry("began", "begin"), Map.entry("begun", "begin"), Map.entry("beginning", "begin"), Map.entry("begins", "begin"),
            Map.entry("drank", "drink"), Map.entry("drunk", "drink"), Map.entry("drinking", "drink"), Map.entry("drinks", "drink"),
            Map.entry("sang", "sing"), Map.entry("sung", "sing"), Map.entry("singing", "sing"), Map.entry("sings", "sing"),
            Map.entry("rang", "ring"), Map.entry("rung", "ring"), Map.entry("ringing", "ring"), Map.entry("rings", "ring"),
            Map.entry("sank", "sink"), Map.entry("sunk", "sink"), Map.entry("sinking", "sink"), Map.entry("sinks", "sink"),
            Map.entry("struck", "strike"), Map.entry("stricken", "strike"), Map.entry("striking", "strike"), Map.entry("strikes", "strike"),
            Map.entry("became", "become"), Map.entry("becoming", "become"), Map.entry("becomes", "become"),
            Map.entry("came", "come"), Map.entry("coming", "come"), Map.entry("comes", "come"),
            Map.entry("led", "lead"), Map.entry("leading", "lead"), Map.entry("leads", "lead"),
            Map.entry("held", "hold"), Map.entry("holding", "hold"), Map.entry("holds", "hold"),
            Map.entry("fed", "feed"), Map.entry("feeding", "feed"), Map.entry("feeds", "feed"),
            Map.entry("slunk", "slink"), Map.entry("slinking", "slink"), Map.entry("slinks", "slink"),
            Map.entry("bound", "bind"), Map.entry("binding", "bind"), Map.entry("binds", "bind"),
            Map.entry("spun", "spin"), Map.entry("spinning", "spin"), Map.entry("spins", "spin"),
            Map.entry("stole", "steal"), Map.entry("stolen", "steal"), Map.entry("stealing", "steal"), Map.entry("steals", "steal"),
            Map.entry("woke", "wake"), Map.entry("woken", "wake"), Map.entry("waking", "wake"), Map.entry("wakes", "wake"),
            Map.entry("shook", "shake"), Map.entry("shaken", "shake"), Map.entry("shaking", "shake"), Map.entry("shakes", "shake"),
            Map.entry("rose", "rise"), Map.entry("risen", "rise"), Map.entry("rising", "rise"), Map.entry("rises", "rise"),

            // 不规则复数
            Map.entry("children", "child"),
            Map.entry("people", "person"),
            Map.entry("men", "man"),
            Map.entry("women", "woman"),
            Map.entry("teeth", "tooth"),
            Map.entry("feet", "foot"),
            Map.entry("mice", "mouse"),
            Map.entry("geese", "goose"),
            Map.entry("oxen", "ox"),
            Map.entry("knives", "knife"),
            Map.entry("wives", "wife"),
            Map.entry("lives", "life"),
            Map.entry("thieves", "thief"),
            Map.entry("halves", "half"),
            Map.entry("calves", "calf"),
            Map.entry("wolves", "wolf"),
            Map.entry("shelves", "shelf"),
            Map.entry("loaves", "loaf"),
            Map.entry("scarves", "scarf"),
            Map.entry("analyses", "analysis"),
            Map.entry("crises", "crisis"),
            Map.entry("diagnoses", "diagnosis"),
            Map.entry("hypotheses", "hypothesis"),
            Map.entry("oases", "oasis"),
            Map.entry("theses", "thesis"),
            Map.entry("phenomena", "phenomenon"),
            Map.entry("criteria", "criterion"),
            Map.entry("data", "datum"),
            Map.entry("media", "medium"),
            Map.entry("matrices", "matrix"),
            Map.entry("vertices", "vertex"),
            Map.entry("indices", "index"),

            // 不规则形容词/副词比较级最高级
            Map.entry("better", "good"), Map.entry("best", "good"),
            Map.entry("worse", "bad"), Map.entry("worst", "bad"),
            Map.entry("more", "many"), Map.entry("most", "many"),
            Map.entry("less", "little"), Map.entry("least", "little"),
            Map.entry("further", "far"), Map.entry("furthest", "far"),
            Map.entry("farther", "far"), Map.entry("farthest", "far"),
            Map.entry("elder", "old"), Map.entry("eldest", "old")
    );

    /**
     * 推导单词的原形候选列表（按置信度排序）。
     * 若包含不规则映射，优先返回精确映射的原形；若有多种规则还原可能，依次返回所有有效候选。
     */
    public List<String> candidateLemmas(String rawTerm) {
        if (!StringUtils.hasText(rawTerm)) {
            return Collections.emptyList();
        }
        String term = rawTerm.trim().toLowerCase(Locale.ROOT);
        Set<String> candidates = new LinkedHashSet<>();

        // 1. 不规则表直查
        if (IRREGULAR_MAP.containsKey(term)) {
            candidates.add(IRREGULAR_MAP.get(term));
        }

        // 2. 动词 -ing 还原
        if (term.endsWith("ing") && term.length() > 4) {
            String stem = term.substring(0, term.length() - 3);
            if (term.endsWith("ying") && stem.length() >= 1) {
                candidates.add(stem.substring(0, stem.length() - 1) + "ie"); // e.g., tying -> tie, dying -> die
            }
            if (hasDoubleConsonant(stem)) {
                candidates.add(stem.substring(0, stem.length() - 1)); // e.g., running -> run, stopping -> stop
            }
            candidates.add(stem + "e"); // e.g., making -> make, dancing -> dance, writing -> write
            candidates.add(stem);       // e.g., playing -> play, eating -> eat, looking -> look
        }

        // 3. 动词/形容词 -ied / -ies 还原
        if (term.endsWith("ied") && term.length() > 4) {
            candidates.add(term.substring(0, term.length() - 3) + "y"); // e.g., studied -> study, carried -> carry
            candidates.add(term.substring(0, term.length() - 1));       // e.g., died -> die, tied -> tie
        }
        if (term.endsWith("ies") && term.length() > 4) {
            candidates.add(term.substring(0, term.length() - 3) + "y"); // e.g., carries -> carry, stories -> story, cities -> city
            candidates.add(term.substring(0, term.length() - 1));       // e.g., dies -> die, ties -> tie
        }

        // 4. -ves 还原
        if (term.endsWith("ves") && term.length() > 4) {
            candidates.add(term.substring(0, term.length() - 3) + "f");  // e.g., leaves -> leaf
            candidates.add(term.substring(0, term.length() - 3) + "fe"); // e.g., lives -> life, knives -> knife
        }

        // 5. 动词过去式/过去分词 -ed 还原
        if (term.endsWith("ed") && term.length() > 3) {
            String stem = term.substring(0, term.length() - 2);
            if (hasDoubleConsonant(stem)) {
                candidates.add(stem.substring(0, stem.length() - 1)); // e.g., stopped -> stop, planned -> plan
            }
            candidates.add(stem + "e"); // e.g., loved -> love, hated -> hate, created -> create
            candidates.add(stem);       // e.g., played -> play, looked -> look, needed -> need
        }

        // 6. 比较级/最高级 -ier/-iest, -er/-est 还原
        if (term.endsWith("ier") && term.length() > 4) {
            candidates.add(term.substring(0, term.length() - 3) + "y"); // e.g., happier -> happy, easier -> easy
        }
        if (term.endsWith("iest") && term.length() > 5) {
            candidates.add(term.substring(0, term.length() - 4) + "y"); // e.g., happiest -> happy, easiest -> easy
        }
        if (term.endsWith("er") && term.length() > 4 && !term.endsWith("ier")) {
            String stem = term.substring(0, term.length() - 2);
            if (hasDoubleConsonant(stem)) {
                candidates.add(stem.substring(0, stem.length() - 1)); // e.g., bigger -> big, hotter -> hot
            }
            candidates.add(stem + "e"); // e.g., nicer -> nice, larger -> large
            candidates.add(stem);       // e.g., faster -> fast, cleaner -> clean
        }
        if (term.endsWith("est") && term.length() > 5 && !term.endsWith("iest")) {
            String stem = term.substring(0, term.length() - 3);
            if (hasDoubleConsonant(stem)) {
                candidates.add(stem.substring(0, stem.length() - 1)); // e.g., biggest -> big
            }
            candidates.add(stem + "e"); // e.g., nicest -> nice, largest -> large
            candidates.add(stem);       // e.g., fastest -> fast
        }

        // 7. -es / -s 还原
        if (term.endsWith("es") && term.length() > 3) {
            if (term.endsWith("sses") || term.endsWith("shes") || term.endsWith("ches") || term.endsWith("xes") || term.endsWith("zes")) {
                candidates.add(term.substring(0, term.length() - 2)); // e.g., watches -> watch, boxes -> box, passes -> pass
            } else if (term.endsWith("oes")) {
                candidates.add(term.substring(0, term.length() - 2)); // e.g., heroes -> hero, tomatoes -> tomato
            }
            candidates.add(term.substring(0, term.length() - 1));     // e.g., makes -> make, plates -> plate
            candidates.add(term.substring(0, term.length() - 2));     // general drop -es
        }
        if (term.endsWith("s") && term.length() > 2 && !term.endsWith("ss") && !term.endsWith("us") && !term.endsWith("is") && !term.endsWith("as")) {
            candidates.add(term.substring(0, term.length() - 1));     // e.g., apples -> apple, cats -> cat, books -> book
        }

        // 排除与原始词完全一致的项
        candidates.remove(term);
        return new ArrayList<>(candidates);
    }

    private boolean hasDoubleConsonant(String stem) {
        if (stem == null || stem.length() < 2) {
            return false;
        }
        char last = stem.charAt(stem.length() - 1);
        char secondLast = stem.charAt(stem.length() - 2);
        return last == secondLast && isConsonant(last);
    }

    private boolean isConsonant(char c) {
        return c >= 'a' && c <= 'z' && c != 'a' && c != 'e' && c != 'i' && c != 'o' && c != 'u' && c != 'y';
    }
}

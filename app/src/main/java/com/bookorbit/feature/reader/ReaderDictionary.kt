package com.bookorbit.feature.reader

import com.bookorbit.feature.dictionary.Dictionaries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

data class DictionaryDefinition(val definition: String, val example: String?)

/**
 * One part of speech's senses. [sourceWord] differs from the looked-up word when the entry came
 * from following it to another word ("walked" to "walk", "assiduously" to "assiduous").
 */
data class DictionaryEntry(val partOfSpeech: String, val definitions: List<DictionaryDefinition>, val sourceWord: String)

data class DictionaryResult(val word: String, val phonetic: String?, val entries: List<DictionaryEntry>)

/** What looking a word up came to. */
sealed interface DictionaryLookup {
    data class Found(val result: DictionaryResult) : DictionaryLookup

    /** The dictionary for [lang] is on the device but doesn't have the word. */
    data class NotFound(val lang: String) : DictionaryLookup

    /** There's no dictionary for [lang] on the device yet. */
    data class NotInstalled(val lang: String) : DictionaryLookup
}

/** An entry as a dictionary file stores it: one word and part of speech. */
internal data class StoredEntry(val word: String, val pos: String, val ipa: String?, val senses: List<DictionaryDefinition>)

/** A word that's a form of [lemma], with its gloss ("plural of cat") when the dictionary gives one. */
internal data class StoredForm(val word: String, val pos: String?, val lemma: String, val gloss: String?)

internal data class StoredMatch(val entries: List<StoredEntry>, val forms: List<StoredForm>) {
    fun isEmpty() = entries.isEmpty() && forms.isEmpty()
}

/** A downloaded dictionary, read on the device. */
internal interface DictionarySource {
    /** Entries and forms whose lowercased spelling is [key]. */
    fun find(key: String): StoredMatch

    /** The same, ignoring accents: [folded] is a key with its accents removed. */
    fun findFolded(folded: String): StoredMatch
}

/**
 * Looks a word up in the dictionary downloaded for the book's language, entirely on the device: no
 * request leaves it. See [DictionarySearch] for how a word is matched and followed to the words
 * that define it.
 */
@Singleton
class ReaderDictionary @Inject constructor(
    private val dictionaries: Dictionaries,
) {
    suspend fun lookup(word: String, bookLanguage: String?): DictionaryLookup = withContext(Dispatchers.IO) {
        val lang = DictionaryParsing.normalizeLang(bookLanguage)
        val source = dictionaries.open(lang) ?: return@withContext DictionaryLookup.NotInstalled(lang)
        DictionarySearch.lookup(source, word, lang)?.let { DictionaryLookup.Found(it) } ?: DictionaryLookup.NotFound(lang)
    }
}

/**
 * Finds the selected word and, when its entry only points at another word, that word too, so the
 * reader always gets a real definition:
 *  - an inflected form leads to its lemma ("walked": "simple past of walk", then walk's senses);
 *  - a derived word whose definition is just its base ("assiduously": "In an assiduous manner")
 *    leads to the base word's definitions;
 *  - a word the dictionary doesn't have under its exact spelling is tried in lower case, without a
 *    possessive, without accents and, in English, with common endings taken off.
 */
internal object DictionarySearch {
    private const val MAX_DEFINITIONS_PER_ENTRY = 6
    private const val MAX_LEMMA_DEPTH = 2
    private const val MAX_LEMMA_LOOKUPS = 4

    fun lookup(source: DictionarySource, raw: String, lang: String): DictionaryResult? {
        val cleaned = DictionaryParsing.cleanWord(raw)
        if (cleaned.isEmpty()) return null
        for (candidate in DictionaryParsing.candidates(cleaned, lang)) {
            val match = source.find(candidate).takeUnless { it.isEmpty() }
                ?: source.findFolded(DictionaryParsing.fold(candidate)).takeUnless { it.isEmpty() }
                ?: continue
            return expand(source, candidate, match, lang)
        }
        return null
    }

    private fun expand(source: DictionarySource, key: String, root: StoredMatch, lang: String): DictionaryResult? {
        val entries = mutableListOf<DictionaryEntry>()
        val word = root.entries.firstOrNull()?.word ?: root.forms.firstOrNull()?.word ?: key
        var phonetic = root.entries.firstNotNullOfOrNull { it.ipa }
        val visited = mutableSetOf(key.lowercase())
        val queue = ArrayDeque(listOf(Triple(word, root, 0)))
        var lookups = 0
        while (queue.isNotEmpty()) {
            val (shown, match, depth) = queue.removeFirst()
            val blocks = blocksOf(match)
            blocks.forEach { entries += DictionaryEntry(it.partOfSpeech, it.definitions.take(MAX_DEFINITIONS_PER_ENTRY), shown) }
            if (depth >= MAX_LEMMA_DEPTH) continue
            for (lemma in pointersOf(match, blocks, lang)) {
                if (lookups >= MAX_LEMMA_LOOKUPS) break
                if (!visited.add(lemma.lowercase())) continue
                lookups++
                val found = source.find(lemma.lowercase()).takeUnless { it.isEmpty() } ?: continue
                if (phonetic == null) phonetic = found.entries.firstNotNullOfOrNull { it.ipa }
                queue.addLast(Triple(found.entries.firstOrNull()?.word ?: lemma, found, depth + 1))
            }
        }
        if (entries.isEmpty()) return null
        return DictionaryResult(word, phonetic, entries)
    }

    /** A match as sense blocks: real definitions first, then what it's a form of. */
    private fun blocksOf(match: StoredMatch): List<SenseBlock> {
        val defined = match.entries.map { SenseBlock(it.pos, it.senses) }
        val forms = match.forms.mapNotNull { form ->
            form.gloss?.let { SenseBlock(form.pos.orEmpty(), listOf(DictionaryDefinition(it, null))) }
        }
        return DictionaryParsing.mergeBlocks(defined + forms)
    }

    /** The words this match leads to: its lemmas, then the bases its leading definitions name. */
    private fun pointersOf(match: StoredMatch, blocks: List<SenseBlock>, lang: String): List<String> {
        val lemmas = match.forms.map { it.lemma }
        val bases = blocks.mapNotNull { block ->
            block.definitions.firstOrNull()?.definition?.let {
                DictionaryParsing.pointerLemma(it) ?: DictionaryParsing.derivationBase(it, lang)
            }
        }
        return (lemmas + bases).distinct()
    }
}

internal data class SenseBlock(val partOfSpeech: String, val definitions: List<DictionaryDefinition>)

internal object DictionaryParsing {
    private val LANGUAGE_ALIASES: Map<String, String> = buildMap {
        fun alias(code: String, vararg names: String) = names.forEach { put(it, code) }
        alias("ar", "ar", "ara", "arabic")
        alias("cs", "cs", "ces", "cze", "czech")
        alias("da", "da", "dan", "danish")
        alias("de", "de", "deu", "ger", "german")
        alias("el", "el", "ell", "gre", "greek")
        alias("en", "en", "eng", "english")
        alias("es", "es", "spa", "spanish")
        alias("fi", "fi", "fin", "finnish")
        alias("fr", "fr", "fra", "fre", "french")
        alias("he", "he", "heb", "hebrew")
        alias("hi", "hi", "hin", "hindi")
        alias("hu", "hu", "hun", "hungarian")
        alias("id", "id", "ind", "indonesian")
        alias("it", "it", "ita", "italian")
        alias("ja", "ja", "jpn", "japanese")
        alias("ko", "ko", "kor", "korean")
        alias("la", "la", "lat", "latin")
        alias("nl", "nl", "nld", "dut", "dutch")
        alias("no", "no", "nor", "nb", "nob", "norwegian")
        alias("pl", "pl", "pol", "polish")
        alias("pt", "pt", "por", "portuguese")
        alias("ro", "ro", "ron", "rum", "romanian")
        alias("ru", "ru", "rus", "russian")
        alias("sv", "sv", "swe", "swedish")
        alias("th", "th", "tha", "thai")
        alias("tr", "tr", "tur", "turkish")
        alias("uk", "uk", "ukr", "ukrainian")
        alias("vi", "vi", "vie", "vietnamese")
        alias("zh", "zh", "zho", "chi", "chinese")
    }

    /**
     * Words allowed before "of" in an inflection gloss such as "simple past and past participle of
     * walk". Requiring every leading word to be grammatical keeps ordinary definitions ("Informal form
     * of address used by...") from being mistaken for pointers. The regional spelling words are here
     * too, so "UK standard spelling of endeavored" (common in older English books) leads to the word's
     * definition.
     */
    private val GRAMMAR_TERMS = setOf(
        "a", "an", "the", "and", "or", "ablative", "abbreviation", "accusative", "acronym", "active",
        "adjective", "adverb", "alternate", "alternative", "aorist", "archaic", "attributive", "augmentative",
        "capitalized", "clipping", "common", "comparative", "conditional", "conjugation", "construct",
        "contraction", "dated", "dative", "declension", "definite", "diminutive", "dual", "elative", "essive",
        "feminine", "first-person", "form", "forms", "future", "genitive", "gerund", "illative", "imperative",
        "imperfect", "imperfective", "indefinite", "indicative", "infinitive", "inflected", "inflection",
        "inflections", "initialism", "instrumental", "letter-case", "locative", "lowercase", "masculine",
        "misspelling", "neuter", "nominative", "nonstandard", "noun", "obsolete", "participle", "particle",
        "partitive", "passive", "past", "perfect", "perfective", "pluperfect", "plural", "positive",
        "prepositional", "present", "preterite", "second-person", "simple", "singular", "spelling",
        "substantive", "superlative", "supine", "tense", "third-person", "uppercase", "verb", "verbal", "vocative",
        "american", "australian", "british", "canadian", "commonwealth", "standard", "uk", "us",
    )

    private val FILLER_TERMS = setOf("a", "an", "the", "and", "or")

    private val POINTER_PATTERN =
        Regex("""^(?:\([^)]*\)\s*)?([\p{L}\p{M}/ -]+?)\s+of\s+([\p{L}\p{M}][\p{L}\p{M}'’-]*)\s*[.:;,]?$""")

    private const val WORD = """([\p{L}\p{M}][\p{L}\p{M}'’-]*)"""

    /**
     * English definitions that only restate a word through its base: "In an assiduous manner",
     * "The quality of being brave", "Not happy", "Somewhat red", "Of or relating to history". The
     * whole first clause has to be the pattern, so a real definition that merely starts the same
     * way ("Not having been...") isn't taken for one.
     */
    private val DERIVATION_PATTERNS = listOf(
        Regex("""^in an? $WORD (?:manner|way|fashion)$"""),
        Regex("""^(?:the )?(?:state|quality|condition|fact|property|characteristic|habit|degree) of being (?:an? )?$WORD$"""),
        Regex("""^(?:the )?(?:act|action|process|practice) of $WORD$"""),
        Regex("""^not $WORD$"""),
        Regex("""^(?:somewhat|rather|slightly|very) $WORD$"""),
        Regex("""^(?:of|relating to|pertaining to|of or relating to|of or pertaining to|characteristic of|resembling) (?:an? |the )?$WORD$"""),
        Regex("""^(?:full of|having|characterized by|characterised by|lacking|without) (?:an? |the )?$WORD$"""),
    )

    /** English endings a reader's word may carry that its dictionary entry doesn't, longest first. */
    private val ENGLISH_ENDINGS = listOf(
        "ically" to "ic", "ily" to "y", "ies" to "y", "ied" to "y", "ier" to "y", "iest" to "y",
        "ly" to "", "ness" to "", "ments" to "ment", "ing" to "", "ing" to "e", "ed" to "", "ed" to "e",
        "est" to "", "er" to "", "es" to "", "s" to "",
    )

    /** A selection's word without the punctuation a long-press often takes with it. */
    fun cleanWord(word: String): String = word.trim().trim { !it.isLetterOrDigit() && it != '\'' && it != '’' && it != '-' }.trim('\'', '’', '-')

    /** The book's language as a dictionary code; English when unknown. */
    fun normalizeLang(lang: String?): String {
        val normalized = lang?.trim()?.lowercase().orEmpty()
        if (normalized.isEmpty()) return "en"
        val primary = normalized.split(Regex("[;,/|]")).first().trim()
        if (primary.isEmpty()) return "en"
        val base = primary.split('-', '_').first().trim()
        LANGUAGE_ALIASES[base]?.let { return it }
        return if (Regex("^[a-z]{2,3}$").matches(base)) base else "en"
    }

    /** [text] without accents, as the dictionary's fold columns store it. */
    fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "")

    /** Spellings to try, in order: as selected (lowercased), without a possessive, then English stems. */
    fun candidates(word: String, lang: String): List<String> {
        val lower = word.lowercase()
        val out = linkedSetOf(lower)
        val bare = lower.removeSuffix("'s").removeSuffix("’s").trimEnd('\'', '’')
        out += bare
        if (lang == "en") {
            for ((ending, replacement) in ENGLISH_ENDINGS) {
                if (bare.length > ending.length + 2 && bare.endsWith(ending)) {
                    val stem = bare.dropLast(ending.length) + replacement
                    out += stem
                    // "stopped" -> "stopp" -> "stop"
                    if (replacement.isEmpty() && stem.length > 2 && stem.last() == stem[stem.length - 2]) out += stem.dropLast(1)
                }
            }
        }
        return out.filter { it.isNotEmpty() }
    }

    /**
     * The lemma a definition merely points at, or null when it stands on its own. "plural of
     * candelabra" tells the reader nothing, so the lookup follows it; "A candle holder." does not.
     */
    fun pointerLemma(definition: String): String? {
        val match = POINTER_PATTERN.find(definition.trim()) ?: return null
        val modifiers = match.groupValues[1].lowercase().split(Regex("[\\s/]+")).filter { it.isNotEmpty() }
        if (modifiers.isEmpty()) return null
        if (!modifiers.all { it in GRAMMAR_TERMS }) return null
        if (modifiers.all { it in FILLER_TERMS }) return null
        return match.groupValues[2].lowercase()
    }

    /** The base word an English definition only restates ("In an assiduous manner" -> "assiduous"). */
    fun derivationBase(definition: String, lang: String): String? {
        if (lang != "en") return null
        val head = definition.trim()
            .replace(Regex("""^(?:\([^)]*\)\s*)+"""), "")
            .split(';', ',', ':').first()
            .trim().trimEnd('.').trim()
            .lowercase()
        return DERIVATION_PATTERNS.firstNotNullOfOrNull { it.matchEntire(head)?.groupValues?.get(1) }
    }

    /**
     * Collapses blocks that share a part of speech: a dictionary splits a word across several
     * same-part-of-speech entries (one per etymology), which would otherwise repeat "NOUN" headings.
     */
    fun mergeBlocks(blocks: List<SenseBlock>): List<SenseBlock> {
        val merged = LinkedHashMap<String, MutableList<DictionaryDefinition>>()
        for (block in blocks) {
            if (block.definitions.isEmpty()) continue
            merged.getOrPut(block.partOfSpeech) { mutableListOf() } += block.definitions
        }
        return merged.map { (pos, defs) -> SenseBlock(pos, defs) }
    }
}

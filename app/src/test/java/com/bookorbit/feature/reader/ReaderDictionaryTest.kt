package com.bookorbit.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderDictionaryTest {

    /** A dictionary in memory, keyed like the downloaded files: lowercased words, accents folded aside. */
    private class FakeSource(
        private val entries: List<StoredEntry> = emptyList(),
        private val forms: List<StoredForm> = emptyList(),
    ) : DictionarySource {
        override fun find(key: String) =
            StoredMatch(entries.filter { it.word.lowercase() == key }, forms.filter { it.word.lowercase() == key })

        override fun findFolded(folded: String) = StoredMatch(
            entries.filter { DictionaryParsing.fold(it.word.lowercase()) == folded },
            forms.filter { DictionaryParsing.fold(it.word.lowercase()) == folded },
        )
    }

    private fun entry(word: String, pos: String, vararg glosses: String, ipa: String? = null) =
        StoredEntry(word, pos, ipa, glosses.map { DictionaryDefinition(it, null) })

    @Test
    fun `book languages map to dictionary codes`() {
        assertEquals("en", DictionaryParsing.normalizeLang(null))
        assertEquals("en", DictionaryParsing.normalizeLang("en-GB"))
        assertEquals("fr", DictionaryParsing.normalizeLang("fre"))
        assertEquals("de", DictionaryParsing.normalizeLang("German"))
        assertEquals("la", DictionaryParsing.normalizeLang("lat"))
        assertEquals("no", DictionaryParsing.normalizeLang("nb_NO"))
        assertEquals("es", DictionaryParsing.normalizeLang("es; en"))
        assertEquals("en", DictionaryParsing.normalizeLang("not a language"))
    }

    @Test
    fun `only grammatical glosses count as pointers`() {
        assertEquals("candelabrum", DictionaryParsing.pointerLemma("plural of candelabrum"))
        assertEquals("walk", DictionaryParsing.pointerLemma("simple past and past participle of walk."))
        assertEquals("be", DictionaryParsing.pointerLemma("(archaic) second-person singular of be"))
        assertEquals("endeavored", DictionaryParsing.pointerLemma("UK standard spelling of endeavored."))
        assertNull(DictionaryParsing.pointerLemma("Informal form of address used by friends"))
        assertNull(DictionaryParsing.pointerLemma("A candle holder."))
        assertNull(DictionaryParsing.pointerLemma("the of thing"))
    }

    @Test
    fun `definitions that only restate a base word lead to it`() {
        assertEquals("assiduous", DictionaryParsing.derivationBase("In an assiduous manner; diligently, industriously.", "en"))
        assertEquals("brave", DictionaryParsing.derivationBase("The quality of being brave.", "en"))
        assertEquals("happy", DictionaryParsing.derivationBase("Not happy; sad.", "en"))
        assertEquals("red", DictionaryParsing.derivationBase("Somewhat red.", "en"))
        assertEquals("history", DictionaryParsing.derivationBase("(dated) Of or pertaining to history.", "en"))
        assertEquals("joy", DictionaryParsing.derivationBase("Full of joy.", "en"))
        // A real definition that only starts the same way stands on its own.
        assertNull(DictionaryParsing.derivationBase("Not having been seen before by anyone.", "en"))
        assertNull(DictionaryParsing.derivationBase("Hard-working, diligent or regular; industrious.", "en"))
        assertNull(DictionaryParsing.derivationBase("In an assiduous manner", "fr"))
    }

    @Test
    fun `a selection loses the punctuation around the word`() {
        assertEquals("doubt", DictionaryParsing.cleanWord(" “doubt,” "))
        assertEquals("don't", DictionaryParsing.cleanWord("don't."))
    }

    @Test
    fun `spellings to try drop possessives and, in English, common endings`() {
        val tried = DictionaryParsing.candidates("Gatsby's", "en")
        assertEquals(listOf("gatsby's", "gatsby"), tried.take(2))
        assertTrue("stop" in DictionaryParsing.candidates("stopped", "en"))
        assertTrue("happy" in DictionaryParsing.candidates("happily", "en"))
        assertEquals(listOf("mangeait"), DictionaryParsing.candidates("mangeait", "fr"))
    }

    @Test
    fun `an adverb defined by its adjective shows both`() {
        val source = FakeSource(
            entries = listOf(
                entry("assiduously", "adv", "In an assiduous manner; diligently, industriously."),
                entry("assiduous", "adj", "Hard-working, diligent or regular (in attendance or work); industrious.", ipa = "/əˈsɪdjuəs/"),
            ),
        )
        val result = DictionarySearch.lookup(source, "assiduously,", "en")!!
        assertEquals(listOf("assiduously", "assiduous"), result.entries.map { it.sourceWord })
        assertEquals("Hard-working, diligent or regular (in attendance or work); industrious.", result.entries[1].definitions.single().definition)
        assertEquals("/əˈsɪdjuəs/", result.phonetic)
    }

    @Test
    fun `an inflected form leads to its lemma, through chains`() {
        val source = FakeSource(
            entries = listOf(entry("candelabrum", "noun", "A branched candlestick.")),
            forms = listOf(
                StoredForm("candelabras", "noun", "candelabra", "plural of candelabra"),
                StoredForm("candelabra", "noun", "candelabrum", "plural of candelabrum"),
            ),
        )
        val result = DictionarySearch.lookup(source, "candelabras", "en")!!
        assertEquals(listOf("candelabras", "candelabra", "candelabrum"), result.entries.map { it.sourceWord })
        assertEquals("plural of candelabra", result.entries[0].definitions.single().definition)
    }

    @Test
    fun `a form from an inflection table goes straight to its lemma`() {
        val source = FakeSource(
            entries = listOf(entry("manger", "verb", "to eat")),
            forms = listOf(StoredForm("mangeait", "verb", "manger", null)),
        )
        val result = DictionarySearch.lookup(source, "mangeait", "fr")!!
        assertEquals(listOf("manger"), result.entries.map { it.sourceWord })
    }

    @Test
    fun `words are found without their accents, capitals or endings`() {
        val source = FakeSource(entries = listOf(entry("naive", "adj", "Lacking worldly experience."), entry("stop", "verb", "To cease moving.")))
        assertEquals("naive", DictionarySearch.lookup(source, "Naïve", "en")!!.word)
        assertEquals("stop", DictionarySearch.lookup(source, "stopped", "en")!!.word)
        assertNull(DictionarySearch.lookup(source, "zzz", "en"))
    }

    @Test
    fun `a word that both defines itself and is a form keeps both`() {
        val source = FakeSource(
            entries = listOf(entry("left", "adj", "Of the side to the west when facing north."), entry("leave", "verb", "To go away from.")),
            forms = listOf(StoredForm("left", "verb", "leave", "simple past and past participle of leave")),
        )
        val result = DictionarySearch.lookup(source, "left", "en")!!
        assertEquals(listOf("adj", "verb", "verb"), result.entries.map { it.partOfSpeech })
        assertEquals(listOf("left", "left", "leave"), result.entries.map { it.sourceWord })
    }
}

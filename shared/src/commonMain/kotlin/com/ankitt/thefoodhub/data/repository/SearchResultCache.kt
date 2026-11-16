package com.ankitt.thefoodhub.data.repository

import com.ankitt.thefoodhub.domain.model.RecipePage
import com.ankitt.thefoodhub.domain.model.SearchCriteria
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Recent search pages kept in memory, so repeating a search costs no quota: fixing a typo back
 * to an earlier query, returning to the Search tab, or paging through results seen before.
 *
 * Holds at most [maxEntries] pages, dropping the least recently used, and treats pages older
 * than [timeToLive] as missing. Search results change slowly, but not never.
 */
internal class SearchResultCache(
    private val clock: Clock,
    private val maxEntries: Int = 30,
    private val timeToLive: Duration = 15.minutes,
) {
    private data class Key(val criteria: SearchCriteria, val offset: Int, val pageSize: Int)
    private class Entry(val page: RecipePage, val storedAtEpochMillis: Long)

    private val mutex = Mutex()

    /** Insertion order is use order: [get] moves a hit to the end, so the first entry is the least recently used. */
    private val entries = LinkedHashMap<Key, Entry>()

    suspend fun get(criteria: SearchCriteria, offset: Int, pageSize: Int): RecipePage? = mutex.withLock {
        val key = Key(criteria.normalized(), offset, pageSize)
        val entry = entries.remove(key) ?: return null
        if (clock.now().toEpochMilliseconds() - entry.storedAtEpochMillis >= timeToLive.inWholeMilliseconds) return null
        entries[key] = entry
        entry.page
    }

    suspend fun put(criteria: SearchCriteria, offset: Int, pageSize: Int, page: RecipePage) = mutex.withLock {
        val key = Key(criteria.normalized(), offset, pageSize)
        entries.remove(key)
        entries[key] = Entry(page, clock.now().toEpochMilliseconds())
        while (entries.size > maxEntries) entries.remove(entries.keys.first())
    }

    private fun SearchCriteria.normalized(): SearchCriteria = copy(query = SearchCriteria.normalizeQuery(query))
}

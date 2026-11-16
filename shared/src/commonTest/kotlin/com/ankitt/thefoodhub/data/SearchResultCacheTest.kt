package com.ankitt.thefoodhub.data

import com.ankitt.thefoodhub.data.repository.SearchResultCache
import com.ankitt.thefoodhub.domain.model.SearchCriteria
import com.ankitt.thefoodhub.testutil.FixedClock
import com.ankitt.thefoodhub.testutil.recipePage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class SearchResultCacheTest {

    private val clock = FixedClock(nowMillis = 1_000_000)
    private val page = recipePage(1..3)

    @Test
    fun aPageIsFoundRegardlessOfCaseAndSpacing() = runTest {
        val cache = SearchResultCache(clock)
        cache.put(SearchCriteria("Chicken Curry"), offset = 0, pageSize = 20, page)

        assertEquals(page, cache.get(SearchCriteria(" chicken  curry"), offset = 0, pageSize = 20))
        assertNull(cache.get(SearchCriteria("chicken curry"), offset = 20, pageSize = 20), "another page is another entry")
        assertNull(cache.get(SearchCriteria("chicken"), offset = 0, pageSize = 20))
    }

    @Test
    fun pagesExpire() = runTest {
        val cache = SearchResultCache(clock, timeToLive = 15.minutes)
        cache.put(SearchCriteria("soup"), 0, 20, page)

        clock.nowMillis += 15.minutes.inWholeMilliseconds

        assertNull(cache.get(SearchCriteria("soup"), 0, 20))
    }

    @Test
    fun theLeastRecentlyUsedPageMakesRoomForANewOne() = runTest {
        val cache = SearchResultCache(clock, maxEntries = 2)
        cache.put(SearchCriteria("soup"), 0, 20, page)
        cache.put(SearchCriteria("pasta"), 0, 20, page)
        cache.get(SearchCriteria("soup"), 0, 20) // soup is now the most recently used

        cache.put(SearchCriteria("salad"), 0, 20, page)

        assertNull(cache.get(SearchCriteria("pasta"), 0, 20))
        assertEquals(page, cache.get(SearchCriteria("soup"), 0, 20))
        assertEquals(page, cache.get(SearchCriteria("salad"), 0, 20))
    }
}

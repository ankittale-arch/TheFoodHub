package com.ankitt.thefoodhub.testutil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * `viewModelScope` runs on Dispatchers.Main. Replacing it with a test dispatcher lets `runTest`
 * share one virtual-time scheduler with the ViewModel, so debounce delays complete instantly.
 */
abstract class MainDispatcherTest {
    protected val testDispatcher: TestDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setMainDispatcher() = Dispatchers.setMain(testDispatcher)

    @AfterTest
    fun resetMainDispatcher() = Dispatchers.resetMain()
}

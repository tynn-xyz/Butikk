//  Copyright 2019 Christian Schmitz
//  SPDX-License-Identifier: Apache-2.0

package xyz.tynn.butikk

import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Unconfined
import xyz.tynn.butikk.testing.GenericStoreUnitTest
import kotlin.coroutines.coroutineContext
import kotlin.test.*

internal class StoreKtTest : GenericStoreUnitTest<String>("init") {

    val error = "Store update failed"

    @Test
    fun `initialize should initialize the state`() {
        assertEquals(initialState, store.value)
    }

    @Test
    fun `initialize should contain scope`() {
        val name = CoroutineName("name")
        val scope = scope + name

        store = scope.createStore {
            assertEquals(name, coroutineContext[CoroutineName])
            initialState
        }

        assertEquals(initialState, store.value)
    }

    @Test
    fun `initialize should contain scope and context`() {
        val name = CoroutineName("name")
        val scope = scope + CoroutineName("")

        store = scope.createStore(name) {
            assertEquals(name, coroutineContext[CoroutineName])
            initialState
        }

        assertEquals(initialState, store.value)
    }

    @Test
    fun `initialize should contain context`() {
        val name = CoroutineName("name")

        store = scope.createStore(name) {
            assertEquals(name, coroutineContext[CoroutineName])
            initialState
        }

        assertEquals(initialState, store.value)
    }

    @Test
    fun `initialize should cancel the store on error`() = runBlocking {
        store = scope.createStore { throw IllegalArgumentException("Error") }

        assertFailsWith<CancellationException>(error) { store.value }
        assertFailsWith<CancellationException>(error) { store.subscribe { } }
        assertFailsWith<CancellationException>(error) { store.enqueue { this } }
        Unit
    }

    @Test
    fun `value should throw before initialize`() {
        assertFailsWith<IllegalStateException> {
            scope.createStore { delay(1000) }.value
        }
    }

    @Test
    fun `cancel of context should cancel observer`() = runBlocking {
        val launch = launch(Unconfined) { store.subscribe {} }

        scope.cancel(CancellationException())

        assertFalse { launch.isActive }
        assertTrue { launch.isCancelled }
    }

    @Test
    fun `enqueue should update the state`() = runBlocking {
        val update = "update"

        store.enqueue { update }

        assertEquals(update, store.value)
    }

    @Test
    fun `enqueue should cancel the store on error`() = runBlocking {
        store.enqueue { throw IllegalArgumentException("Error") }

        assertFailsWith<CancellationException>(error) { store.value }
        assertFailsWith<CancellationException>(error) { store.subscribe { } }
        assertFailsWith<CancellationException>(error) { store.enqueue { this } }
        Unit
    }

    @Test
    fun `subscribe should observe all updates`() = runBlocking {
        val values = collect<String> { store.subscribe(it) }
        val updates = listOf("update", "update", "end")

        for (update in updates)
            store.enqueue { update }

        assertEquals(listOf(initialState) + updates, values)
    }
}

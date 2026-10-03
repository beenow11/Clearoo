package com.clearoo.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageRulesTest {
    private val mb = 1024L * 1024
    private val gb = 1024 * mb

    @Test
    fun `small phones use percentages`() {
        val total = 8 * gb
        assertEquals(StorageLevel.FULL, StorageRules.level(300 * mb, total))
        assertEquals(StorageLevel.LOW, StorageRules.level(600 * mb, total))
        assertEquals(StorageLevel.OK, StorageRules.level(900 * mb, total))
    }

    @Test
    fun `big phones are capped`() {
        val total = 256 * gb
        assertEquals(StorageLevel.FULL, StorageRules.level(400 * mb, total))
        assertEquals(StorageLevel.LOW, StorageRules.level(1 * gb, total))
        assertEquals(StorageLevel.OK, StorageRules.level(3 * gb, total))
    }

    @Test
    fun `unknown total is ok`() {
        assertEquals(StorageLevel.OK, StorageRules.level(0, 0))
    }
}

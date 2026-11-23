package com.ornek.yzgercekci

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenverseClientTest {

    @Test
    fun keepsOnlyFreeLicensedSecureReasonableClips() {
        val json = """{"result_count":5,"results":[
          {"id":"a1","title":"Wind in ruins","creator":"ali","license":"cc0","url":"https://cdn.freesound.org/a.mp3","duration":42000,"source":"freesound"},
          {"id":"b2","title":"Rain","creator":"x","license":"by","url":"https://x/b.mp3","duration":30000},
          {"id":"c3","title":"Pop","creator":"x","license":"cc0","url":"https://x/c.mp3","duration":800},
          {"id":"d4","title":"Old","creator":"x","license":"pdm","url":"http://x/d.mp3","duration":20000},
          {"id":"e5","title":"Thunder","creator":"wiki","license":"pdm","url":"https://upload.wikimedia.org/e.ogg","source":"wikimedia_audio"}
        ]}"""
        val hits = OpenverseClient.parseSearch(json)
        assertEquals(listOf("a1", "e5"), hits.map { it.id })
        assertTrue(hits[0].credit.contains("CC0"))
        assertTrue(OpenverseClient.parseSearch("bozuk").isEmpty())
    }
}

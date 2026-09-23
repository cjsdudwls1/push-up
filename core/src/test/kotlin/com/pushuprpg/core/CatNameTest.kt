package com.pushuprpg.core

import com.pushuprpg.core.survival.CatName
import kotlin.test.Test
import kotlin.test.assertEquals

class CatNameTest {

    @Test
    fun `the particle follows how the name ends`() {
        assertEquals("가", CatName.subjectParticle("치즈"))
        assertEquals("가", CatName.subjectParticle("고냥이"))
        assertEquals("이", CatName.subjectParticle("호박"))
        assertEquals("이", CatName.subjectParticle("까망"))
        assertEquals("를", CatName.objectParticle("나비"))
        assertEquals("을", CatName.objectParticle("밤톨"))
    }

    @Test
    fun `a name that is not Hangul still gets a particle rather than a crash`() {
        assertEquals("가", CatName.subjectParticle("Luna"))
        assertEquals("가", CatName.subjectParticle("7"))
        assertEquals("가", CatName.subjectParticle(""))
        assertEquals("이", CatName.subjectParticle("호박 "))
    }

    @Test
    fun `a typed name is one short line`() {
        assertEquals("고등어네 막내", CatName.clean("  고등어네\n막내"))
        assertEquals(CatName.MAX_LENGTH, CatName.clean("아주아주아주아주긴이름").length)
        // Mid-typing, a trailing space is the start of the next word, not junk.
        assertEquals("고등어 ", CatName.clean("고등어 "))
        assertEquals("고등어", CatName.finished("고등어 "))
        assertEquals("", CatName.finished("   "))
    }
}

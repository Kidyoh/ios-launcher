package dev.kidyoh.liquid

import dev.kidyoh.liquid.ui.search.MathEval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MathEvalTest {
    private fun eval(s: String) = MathEval.evaluate(s)

    @Test
    fun precedenceAndParentheses() {
        assertEquals(14.0, eval("2+3*4")!!, 1e-9)
        assertEquals(20.0, eval("(2+3)*4")!!, 1e-9)
        assertEquals(512.0, eval("2^3^2")!!, 1e-9)
    }

    @Test
    fun symbolsAndPercent() {
        assertEquals(12.0, eval("3×4")!!, 1e-9)
        assertEquals(2.5, eval("10÷4")!!, 1e-9)
        assertEquals(50.0, eval("200*25%")!!, 1e-9)
        assertEquals(-3.0, eval("-1-2")!!, 1e-9)
        assertEquals(3.0, eval("sqrt(9)")!!, 1e-9)
    }

    @Test
    fun ignoresThingsThatAreNotMath() {
        assertNull(eval("instagram"))
        assertNull(eval("42"))
        assertNull(eval("1/0"))
        assertNull(eval("2+"))
        assertNull(eval(""))
    }

    @Test
    fun formatting() {
        assertEquals("1,234", MathEval.format(1234.0))
        assertEquals("0.333333", MathEval.format(1.0 / 3))
    }
}

package dev.kidyoh.liquid.ui.search

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/** Tiny recursive-descent calculator for Spotlight: + − × ÷ ^ %, parentheses, sqrt. */
object MathEval {
    fun evaluate(input: String): Double? {
        val s = input.replace('×', '*').replace('÷', '/').replace('x', '*').replace(',', '.').replace(" ", "")
        if (s.isEmpty() || s.none { it.isDigit() } || s.none { it in "+-*/^%(s" }) return null
        if (s.all { it.isDigit() || it == '.' }) return null
        return runCatching {
            val p = Parser(s)
            val v = p.expr()
            if (p.pos != s.length || v.isNaN() || v.isInfinite()) null else v
        }.getOrNull()
    }

    fun format(v: Double): String {
        if (abs(v - floor(v)) < 1e-9 && abs(v) < 1e15) return "%,d".format(v.toLong())
        return "%,.6f".format(v).trimEnd('0').trimEnd('.')
    }

    private class Parser(val s: String) {
        var pos = 0
        fun peek() = if (pos < s.length) s[pos] else '\u0000'

        fun expr(): Double {
            var v = term()
            while (true) {
                v = when (peek()) {
                    '+' -> { pos++; v + term() }
                    '-' -> { pos++; v - term() }
                    else -> return v
                }
            }
        }

        fun term(): Double {
            var v = factor()
            while (true) {
                v = when (peek()) {
                    '*' -> { pos++; v * factor() }
                    '/' -> { pos++; val d = factor(); if (d == 0.0) Double.NaN else v / d }
                    else -> return v
                }
            }
        }

        fun factor(): Double {
            var v = unary()
            if (peek() == '^') { pos++; v = v.pow(factor()) }
            while (peek() == '%') { pos++; v /= 100.0 }
            return v
        }

        fun unary(): Double = when (peek()) {
            '-' -> { pos++; -unary() }
            '+' -> { pos++; unary() }
            else -> atom()
        }

        fun atom(): Double {
            if (s.startsWith("sqrt", pos)) { pos += 4; return sqrt(atom()) }
            if (peek() == '(') {
                pos++
                val v = expr()
                require(peek() == ')')
                pos++
                return v
            }
            val start = pos
            while (peek().isDigit() || peek() == '.') pos++
            require(pos > start)
            return s.substring(start, pos).toDouble()
        }
    }
}

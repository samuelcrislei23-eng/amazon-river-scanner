package com.amazonriver.scanner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlin.math.max

class ScannerViewModel : ViewModel() {

    val rawLines = mutableStateListOf<String>()
    var live by mutableStateOf("")
    var auto by mutableStateOf(true)
    var exam by mutableStateOf("")
    var edited by mutableStateOf(false)

    private val seen = mutableListOf<String>()

    fun onFrame(lines: List<String>) {
        live = lines.joinToString("\n")
        if (auto) lines.forEach { addLine(it) }
    }

    /** Captura manual do que está na tela agora. */
    fun captureNow() {
        live.lines().forEach { addLine(it) }
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun addLine(line: String) {
        val n = norm(line)
        if (n.length < 2) return
        for (s in seen) {
            if (s == n || s.contains(n) || n.contains(s)) {
                if (n.length > s.length && n.contains(s) && s.length > 6) replaceSeen(s, n, line)
                return
            }
            if (similar(s, n)) return
        }
        seen += n
        rawLines += line.trim()
        rebuild()
    }

    private fun replaceSeen(old: String, new: String, line: String) {
        val i = seen.indexOf(old)
        if (i >= 0) { seen[i] = new; if (i < rawLines.size) rawLines[i] = line.trim() }
        rebuild()
    }

    private fun similar(a: String, b: String): Boolean {
        val m = max(a.length, b.length)
        if (m < 6 || kotlin.math.abs(a.length - b.length) > m / 5) return false
        return levenshtein(a, b).toDouble() / m < 0.18
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }

    fun rebuild() {
        if (!edited) exam = ExamParser.format(rawLines.toList())
    }

    fun remount() {
        edited = false
        exam = ExamParser.format(rawLines.toList())
    }

    fun onExamEdited(text: String) {
        exam = text
        edited = true
    }

    fun clear() {
        rawLines.clear(); seen.clear(); live = ""; exam = ""; edited = false
    }
}

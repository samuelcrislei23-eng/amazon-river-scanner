package com.amazonriver.scanner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max

/**
 * "IA" do scanner, toda dentro do aparelho:
 * 1) corta linhas sem sentido (TextQuality);
 * 2) junta as leituras repetidas da mesma linha em vários quadros, guardando a melhor versão;
 * 3) só confirma uma linha quando ela aparece em mais de um quadro (ou é muito boa);
 * 4) mantém a ordem de leitura enquanto a câmera anda;
 * 5) monta o texto no padrão de prova (ExamParser).
 */
class ScannerViewModel : ViewModel() {

    companion object { const val MIN_COUNT = 2 }

    private class Cluster(var text: String, var key: String, var score: Double) {
        var count = 0
        var lastFrame = -1
    }

    private val clusters = mutableListOf<Cluster>()
    private val accents = Regex("\\p{M}+")
    private var frame = 0
    private var lastClean: List<Pair<String, Double>> = emptyList()

    var live by mutableStateOf("")
    var auto by mutableStateOf(true)
    var exam by mutableStateOf("")
    var edited by mutableStateOf(false)
    var kept by mutableStateOf(0)
    var cut by mutableStateOf(0)

    fun onFrame(lines: List<String>) {
        frame++
        val clean = ArrayList<Pair<String, Double>>()
        for (l in lines) {
            val s = TextQuality.score(l)
            if (s >= TextQuality.MIN_SCORE) clean += l to s else cut++
        }
        lastClean = clean
        live = clean.joinToString("\n") { it.first }
        if (auto) merge(clean, false)
    }

    /** Aceita agora as linhas boas que estão na tela. */
    fun captureNow() {
        frame++
        merge(lastClean, true)
    }

    private fun key(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(accents, "").lowercase().filter { it.isLetterOrDigit() }

    private fun same(a: String, b: String): Boolean {
        if (a == b) return true
        val mn = minOf(a.length, b.length)
        val mx = max(a.length, b.length)
        if (mn >= 10 && (a.contains(b) || b.contains(a))) return true
        if (mx < 5 || abs(a.length - b.length) > mx / 4) return false
        return levenshtein(a, b).toDouble() / mx < 0.25
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

    private fun findCluster(k: String): Cluster? {
        if (k.length < 2) return null
        for (c in clusters) if (same(c.key, k)) return c
        return null
    }

    private fun merge(items: List<Pair<String, Double>>, force: Boolean) {
        val matches = items.map { findCluster(key(it.first)) }
        var anchor: Cluster? = null

        for (i in items.indices) {
            val text = items[i].first
            val score = items[i].second
            var c = matches[i]
            if (c == null) {
                val k = key(text)
                if (k.length < 2) continue
                val created = Cluster(text, k, score)
                val a = anchor
                val idx = if (a != null) {
                    clusters.indexOf(a) + 1
                } else {
                    var next: Cluster? = null
                    for (j in i + 1 until items.size) { if (matches[j] != null) { next = matches[j]; break } }
                    if (next != null) clusters.indexOf(next) else clusters.size
                }
                clusters.add(idx, created)
                c = created
            } else {
                val better = score > c.score + 0.05 ||
                    (abs(score - c.score) <= 0.05 && text.length > c.text.length)
                if (better) { c.text = text; c.score = score; c.key = key(text) }
            }
            if (c.lastFrame != frame) { c.count++; c.lastFrame = frame }
            if (force) c.count = max(c.count, MIN_COUNT)
            anchor = c
        }
        rebuild()
    }

    private fun visible(): List<Cluster> = clusters.filter {
        it.count >= MIN_COUNT || (it.score >= 0.95 && it.text.split(" ").size >= 5)
    }

    private fun rebuild() {
        val v = visible()
        kept = v.size
        if (!edited) exam = ExamParser.format(v.map { it.text })
    }

    fun remount() {
        edited = false
        exam = ExamParser.format(visible().map { it.text })
    }

    fun onExamEdited(text: String) {
        exam = text
        edited = true
    }

    fun clear() {
        clusters.clear(); lastClean = emptyList()
        live = ""; exam = ""; edited = false; kept = 0; cut = 0
    }
}

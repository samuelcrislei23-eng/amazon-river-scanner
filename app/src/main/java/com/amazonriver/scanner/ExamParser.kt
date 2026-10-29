package com.amazonriver.scanner

/**
 * Interpreta as linhas aprovadas e monta o texto no padrão de prova:
 *
 * QUESTÃO 1
 * enunciado...
 * a) ...
 * b) ...
 */
object ExamParser {

    private val questionWord = Regex("""^\s*quest[aã]o\s*(\d{1,3})\s*[.)\-–:]?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val questionNumber = Regex("""^\s*(\d{1,3})\s*(?:\)|\.\s|[-–:]\s)\s*(.+)$""")
    private val alternative = Regex("""^\s*\(?([a-eA-E])\s*(?:\)|\.\s|[-–]\s)\s*(.+)$""")
    private val inlineMarker = Regex("""(?:^|\s)\(?([a-eA-E])\)\s*""")

    private class Alt(val text: StringBuilder)
    private class Q(var hasNumber: Boolean) {
        val stem = StringBuilder()
        val alts = mutableListOf<Alt>()
    }

    private fun append(sb: StringBuilder, piece: String) {
        val p = piece.trim()
        if (p.isEmpty()) return
        when {
            sb.isEmpty() -> sb.append(p)
            sb.endsWith("-") && p.first().isLowerCase() -> { sb.setLength(sb.length - 1); sb.append(p) }
            else -> sb.append(' ').append(p)
        }
    }

    /** "a) 2  b) 4  c) 6" na mesma linha: separa em alternativas. */
    private fun splitInline(line: String): Pair<String, List<String>>? {
        val ms = inlineMarker.findAll(line).toList()
        if (ms.size < 2) return null
        val letters = ms.map { it.groupValues[1].lowercase()[0] }
        for (i in 1 until letters.size) if (letters[i] != letters[i - 1] + 1) return null
        val prefix = line.substring(0, ms[0].range.first).trim()
        val parts = ms.indices.map { i ->
            val start = ms[i].range.last + 1
            val end = if (i + 1 < ms.size) ms[i + 1].range.first else line.length
            line.substring(start, end).trim()
        }
        return prefix to parts
    }

    fun format(lines: List<String>): String {
        val questions = mutableListOf<Q>()
        var cur: Q? = null

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            val inline = splitInline(line)
            val qw = questionWord.matchEntire(line)
            val alt = alternative.matchEntire(line)
            val qn = questionNumber.matchEntire(line)

            when {
                qw != null -> {
                    cur = Q(true).also { questions += it }
                    append(cur.stem, qw.groupValues[2])
                }
                inline != null -> {
                    val q = cur ?: Q(false).also { questions += it; cur = it }
                    if (inline.first.isNotEmpty()) {
                        val last = q.alts.lastOrNull()
                        if (last != null) append(last.text, inline.first) else append(q.stem, inline.first)
                    }
                    inline.second.forEach { q.alts += Alt(StringBuilder(it)) }
                }
                alt != null && cur != null -> {
                    cur.alts += Alt(StringBuilder(alt.groupValues[2].trim()))
                }
                qn != null -> {
                    cur = Q(true).also { questions += it }
                    append(cur.stem, qn.groupValues[2])
                }
                else -> {
                    if (cur == null) cur = Q(false).also { questions += it }
                    val last = cur.alts.lastOrNull()
                    if (last != null) append(last.text, line) else append(cur.stem, line)
                }
            }
        }

        val out = StringBuilder()
        var n = 0
        for (q in questions) {
            if (out.isNotEmpty()) out.append("\n\n")
            if (q.hasNumber) {
                n++
                out.append("QUESTÃO ").append(n).append('\n')
            } else {
                out.append("TEXTO DE APOIO\n")
            }
            out.append(q.stem.toString().trim())
            q.alts.forEachIndexed { i, a ->
                out.append('\n').append('a' + i).append(") ").append(a.text.toString().trim())
            }
        }
        return out.toString().trim()
    }
}

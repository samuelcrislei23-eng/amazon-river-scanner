package com.amazonriver.scanner

/**
 * Interpreta as linhas lidas pelo OCR e monta o texto no padrão de prova:
 *
 * QUESTÃO 1
 * enunciado...
 * a) ...
 * b) ...
 */
object ExamParser {

    private val questionWord = Regex("""^\s*quest[aã]o\s*(\d{1,3})\s*[.)\-–:]?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val questionNumber = Regex("""^\s*(\d{1,3})\s*[.)\-–:]\s+(.*)$""")
    private val alternative = Regex("""^\s*\(?([a-eA-E])\s*[.)\-–]\s*(.+)$""")
    private val alternativeBare = Regex("""^\s*\(\s*([a-eA-E])\s*\)\s*(.+)$""")

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

    fun format(lines: List<String>): String {
        val questions = mutableListOf<Q>()
        var cur: Q? = null

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            val qw = questionWord.matchEntire(line)
            val alt = alternative.matchEntire(line) ?: alternativeBare.matchEntire(line)
            val qn = questionNumber.matchEntire(line)

            when {
                qw != null -> {
                    cur = Q(true).also { questions += it }
                    append(cur.stem, qw.groupValues[2])
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

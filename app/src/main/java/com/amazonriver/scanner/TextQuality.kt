package com.amazonriver.scanner

/**
 * Filtro de "sentido": dá uma nota de 0 a 1 para cada linha lida pelo OCR.
 * Linhas tremidas/borradas (movimento rápido) viram palavras sem vogal, letras
 * trocadas, números colados em letras etc. e recebem nota baixa.
 */
object TextQuality {

    const val MIN_SCORE = 0.62

    private const val VOW = "aeiouáàâãéêíóôõúü"

    private val common = setOf(
        "de", "da", "do", "das", "dos", "a", "o", "as", "os", "e", "é", "em", "um", "uma", "uns", "umas",
        "para", "por", "com", "sem", "que", "se", "na", "no", "nas", "nos", "ao", "aos", "à", "às",
        "pela", "pelo", "pelas", "pelos", "qual", "quais", "quanto", "quantos", "quanta", "quantas",
        "como", "onde", "quando", "mais", "menos", "mas", "ou", "ser", "foi", "são", "tem", "têm",
        "ter", "há", "seu", "sua", "seus", "suas", "ele", "ela", "eles", "elas", "isso", "isto",
        "essa", "esse", "este", "esta", "entre", "sobre", "até", "após", "cada", "todo", "toda",
        "todos", "todas", "não", "sim", "já", "só", "também", "então", "pois", "porque", "nem",
        "muito", "pouco", "maior", "menor", "igual", "valor", "resposta", "alternativa", "alternativas",
        "questão", "texto", "assinale", "calcule", "determine", "considere", "analise", "marque",
        "correta", "incorreta", "verdadeira", "falsa", "afirmativa", "afirmativas", "seguir", "abaixo",
        "acima", "avaliação", "prova", "aluno", "aluna", "nome", "data", "série", "turma", "professor",
        "escola", "disciplina", "matemática", "português", "história", "geografia", "ciências",
        "kg", "km", "cm", "mm", "ml", "mg", "hz", "db", "ph", "sr", "sra", "dr", "dra", "etc", "ltda",
        "sp", "rj", "am", "pr", "rs", "mg", "ano", "anos", "dia", "dias", "mês", "meses"
    )

    private val suffixes = listOf(
        "ção", "ções", "mente", "dade", "ado", "ada", "ido", "ida", "ais", "ões", "ar", "er", "ir",
        "os", "as", "es", "am", "em", "ou", "ia", "ou", "nte", "eza", "ura"
    )

    private val unitToken = Regex("""^\d+[a-zA-Zºª°%]{0,3}$""")
    private val edgeTrim: (Char) -> Boolean = { c -> !c.isLetterOrDigit() && c !in "°ºª%²³" }

    fun score(rawLine: String): Double {
        val tokens = rawLine.trim().split(Regex("\\s+")).map { it.trim(edgeTrim) }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return 0.0

        var weighted = 0.0
        var weights = 0.0
        var bad = 0
        for (t in tokens) {
            val s = tokenScore(t)
            val w = maxOf(1, t.length).toDouble()
            weighted += s * w
            weights += w
            if (s < 0.3) bad++
        }
        var result = weighted / weights - 0.08 * bad
        if (tokens.size < 2) result *= 0.7
        return result.coerceIn(0.0, 1.0)
    }

    fun makesSense(line: String) = score(line) >= MIN_SCORE

    private fun tokenScore(t: String): Double {
        val low = t.lowercase()
        if (low in common) return 1.0
        if (low.all { it.isDigit() || it in "°ºª%²³.,/" }) return 0.85
        if (unitToken.matches(t)) return 0.75

        val hasDigit = t.any { it.isDigit() }
        val hasLetter = t.any { it.isLetter() }
        if (hasDigit && hasLetter) return 0.15

        val w = low.filter { it.isLetter() }
        if (w.isEmpty()) return 0.1
        if (w.length == 1) return 0.4

        val allCaps = t.filter { it.isLetter() }.all { it.isUpperCase() }
        val vowelCount = w.count { it in VOW }
        if (vowelCount == 0) return if (allCaps && w.length <= 4) 0.6 else 0.0

        if (!allCaps && t.drop(1).any { it.isUpperCase() }) return 0.1

        var maxCons = 0; var curCons = 0; var maxVow = 0; var curVow = 0; var maxRep = 1; var rep = 1
        for (i in w.indices) {
            val isV = w[i] in VOW
            if (isV) { curVow++; curCons = 0 } else { curCons++; curVow = 0 }
            maxCons = maxOf(maxCons, curCons); maxVow = maxOf(maxVow, curVow)
            if (i > 0 && w[i] == w[i - 1]) { rep++; maxRep = maxOf(maxRep, rep) } else rep = 1
        }
        if (maxRep >= 3) return 0.1
        if (maxCons >= 5) return 0.1
        if (maxVow >= 5) return 0.1
        if (maxVow >= 4) return 0.3
        val ratio = vowelCount.toDouble() / w.length
        if (w.length >= 4 && (ratio < 0.2 || ratio > 0.8)) return 0.3

        var s = if (maxCons >= 4) 0.7 else 0.85
        if (suffixes.any { low.endsWith(it) }) s = minOf(0.95, s + 0.1)
        return s
    }
}

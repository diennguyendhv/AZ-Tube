package com.az.tube

/**
 * NewPipeExtractor only supports English (en-GB) for YouTube, so YouTube shows English titles for
 * videos whose creators added an English title, or that YouTube auto-translates, even when the
 * original title is Vietnamese.
 *
 * We ask YouTube for Vietnamese instead (original titles), then rewrite the few count and date
 * strings the extractor has to parse ("1,2 Tr lượt xem", "3 ngày trước"…) into the English forms it
 * understands ("1.2M views", "3 days ago"). Only whole values are touched: a JSON string, or a part of
 * a joined line wrapped in Unicode isolates, so titles that merely contain such words stay as they are.
 */
object VietnameseText {
    private const val EN_HL = "\"hl\":\"en-GB\""
    private const val VI_HL = "\"hl\":\"vi\""

    /** Swaps the language YouTube is asked for. Returns null when the body isn't a YouTube request. */
    fun requestBody(body: String): String? = if (body.contains(EN_HL)) body.replace(EN_HL, VI_HL) else null

    const val ACCEPT_LANGUAGE = "vi-VN, vi;q=0.9"

    private const val L = "([\"\u2068])"
    private const val R = "([\"\u2069])"
    private const val SP = "[ \u00A0]"
    private val short = Regex("$L(\\d+(?:,\\d+)?)$SP(N|nghìn|Tr|triệu|T|tỷ)(?:$SP(lượt xem|người đăng ký|người đang xem))?$R")
    private val exact = Regex("$L(\\d{1,3}(?:\\.\\d{3})*)$SP(lượt xem|người đăng ký|người đang xem)$R")
    private val ago = Regex("$L(Đã phát trực tiếp |Công chiếu )?(\\d+)$SP(giây|phút|giờ|ngày|tuần|tháng|năm) trước$R")

    private fun word(w: String) = when (w) {
        "lượt xem" -> "views"
        "người đăng ký" -> "subscribers"
        else -> "watching"
    }

    private fun unit(u: String) = when (u) {
        "giây" -> "seconds"
        "phút" -> "minutes"
        "giờ" -> "hours"
        "ngày" -> "days"
        "tuần" -> "weeks"
        "tháng" -> "months"
        else -> "years"
    }

    /** Rewrites Vietnamese counts and "… trước" dates in a YouTube response into English forms. */
    fun response(text: String): String {
        if (!text.contains("lượt xem") && !text.contains("trước") && !text.contains("người đ") &&
            !text.contains(" N") && !text.contains(" Tr")
        ) return text
        var s = short.replace(text) { m ->
            val (l, num, mult, w, r) = m.destructured
            val suffix = when (mult) {
                "N", "nghìn" -> "K"
                "Tr", "triệu" -> "M"
                else -> "B"
            }
            l + num.replace(',', '.') + suffix + (if (w.isEmpty()) "" else " " + word(w)) + r
        }
        s = exact.replace(s) { m ->
            val (l, num, w, r) = m.destructured
            l + num.replace(".", "") + " " + word(w) + r
        }
        s = ago.replace(s) { m ->
            val (l, prefix, n, u, r) = m.destructured
            val p = when {
                prefix.isEmpty() -> ""
                prefix.startsWith("Đã") -> "Streamed "
                else -> "Premiered "
            }
            "$l$p$n ${unit(u)} ago$r"
        }
        return s.replace("\"Chưa có lượt xem\"", "\"No views\"")
    }
}

package com.tvabierta.app.data

import java.text.Collator
import java.util.Locale

data class Country(val code: String, val name: String, val flag: String) {
    val label: String get() = "$flag $name"
    val playlistUrl: String get() = "$BASE_URL$code.m3u"

    companion object {
        /** Per-country playlists published by github.com/Romaxa55/world_ip_tv (re-checked every few hours). */
        const val BASE_URL = "https://romaxa55.github.io/world_ip_tv/output/"
    }
}

object Countries {
    /** Every per-country file in world_ip_tv's output/ folder ("int" = international channels). */
    private val codes = """
        ad ae af ag ai al am ao ar as at au aw az ba bb bd be bf bg bh bi bj bl bm bn bo bq br bs bt bv bw
        by bz ca cd cf cg ch ci ck cl cm cn co cr cu cv cw cy cz de dj dk dm do dz ec ee eg eh er es et fi
        fj fk fm fo fr ga gd gf gh gl gm gn gp gq gr gs gt gw gy hk hn hr ht hu id ie il in int iq ir is it
        jm jo jp ke kg kh ki km kn kp kr kw ky kz la lb lc li lk lr ls lt lu lv ly ma mc md me mf mg mh mk
        ml mm mn mo mp mq mr ms mt mu mv mw mx my mz na nc ne nf ng ni nl no np nr nu nz om pa pe pf pg ph
        pk pl pm pn pr ps pt pw py qa re ro rs ru rw sa sb sc sd se sg sh si sk sl sm sn so sr ss st sv sx
        sy sz tc td tf tg th tj tk tl tm tn to tr tt tv tw tz ua ug uk us uy uz va vc ve vg vi vn vu wf ws
        xk ye yt za zm zw
    """.trim().split(Regex("""\s+"""))

    private val spanish: Locale = Locale.forLanguageTag("es")

    val all: List<Country> by lazy {
        val collator = Collator.getInstance(spanish).apply { strength = Collator.PRIMARY }
        codes.map { Country(it, nameOf(it), flagOf(it)) }.sortedWith(compareBy(collator) { it.name })
    }

    fun byCode(code: String): Country? = all.firstOrNull { it.code == code }

    /** The device's own country when world_ip_tv has a list for it, otherwise Mexico. */
    fun default(): Country {
        val region = Locale.getDefault().country.lowercase(Locale.ROOT).let { if (it == "gb") "uk" else it }
        return byCode(region) ?: byCode("mx")!!
    }

    // world_ip_tv follows iptv-org and uses "uk" where ISO 3166 says "GB".
    private fun region(code: String) = if (code == "uk") "GB" else code.uppercase(Locale.ROOT)

    private fun nameOf(code: String): String {
        if (code == "int") return "Internacional"
        val region = region(code)
        val name = runCatching { Locale.Builder().setRegion(region).build().getDisplayCountry(spanish) }.getOrNull()
        return if (name.isNullOrBlank() || name == region) region else name.replaceFirstChar { it.titlecase(spanish) }
    }

    private fun flagOf(code: String): String {
        if (code == "int") return "🌐"
        return buildString { region(code).forEach { appendCodePoint(0x1F1E6 + (it - 'A')) } }
    }
}

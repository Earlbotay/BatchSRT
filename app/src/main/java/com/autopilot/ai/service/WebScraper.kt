package com.autopilot.ai.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

object WebScraper {
    private const val TAG = "WebScraper"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/121.0 Mobile Safari/537.36"

    suspend fun searchWeb(query: String): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://html.duckduckgo.com/html/?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(15000)
                .get()

            val results = doc.select(".result__body")
            val sb = StringBuilder()
            results.take(5).forEachIndexed { i, el ->
                val title = el.select(".result__title").text()
                val snippet = el.select(".result__snippet").text()
                sb.append("${i + 1}. $title\n$snippet\n\n")
            }
            sb.toString().ifEmpty { "No results found for: $query" }
        } catch (e: Exception) {
            Log.e(TAG, "Search error", e)
            "Search failed: ${e.message}"
        }
    }

    suspend fun scrapeUrl(url: String): String = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(15000)
                .get()

            val title = doc.title()
            val body = doc.body().text().take(2000)
            "Title: $title\nContent: $body"
        } catch (e: Exception) {
            Log.e(TAG, "Scrape error", e)
            "Scrape failed: ${e.message}"
        }
    }
}

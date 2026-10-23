package com.example.data

import com.example.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GeminiPdfParser {

    suspend fun parseHolidayPdf(pdfBase64: String): List<ParsedHoliday> {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty()) {
            throw IllegalStateException("Gemini API Key is missing. Please configure it in your secrets.")
        }

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "You are given a PDF document containing a holiday list of an organization. " +
                                    "Please analyze this document and extract all holidays listed. " +
                                    "Format the holidays as a JSON array where each object has fields: " +
                                    "\"name\" (string, the name of the holiday, e.g. 'Independence Day') and " +
                                    "\"date\" (string, the exact date formatted 'YYYY-MM-DD', e.g. '2026-07-04'). " +
                                    "If the year of a holiday is not explicitly written beside its date, deduce it from the document header, body context, or current year. " +
                                    "Important: Do NOT include markdown code blocks block (no ```json). Output ONLY the raw JSON array. " +
                                    "Example output format: [{\"name\": \"New Year\", \"date\": \"2026-01-01\"}]")
                        })
                        put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", "application/pdf")
                                put("data", pdfBase64)
                            })
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
            })
        }

        val payload = requestJson.toString()

        // Robust candidate pool of modern capable models to protect against temporary server overloads or 503 outages
        val candidateModels = listOf(
            "gemini-flash-latest",
            "gemini-3.5-flash",
            "gemini-3.1-pro-preview",
            "gemini-2.5-flash"
        )

        var lastException: Throwable? = null

        for (model in candidateModels) {
            try {
                val parsedHolidays = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                    val connection = url.openConnection() as HttpURLConnection
                    try {
                        connection.requestMethod = "POST"
                        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        connection.setRequestProperty("Accept", "application/json")
                        connection.connectTimeout = 60_000
                        connection.readTimeout = 60_000
                        connection.doOutput = true

                        OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                            writer.write(payload)
                            writer.flush()
                        }

                        val responseCode = connection.responseCode
                        if (responseCode !in 200..299) {
                            val errorStream = connection.errorStream ?: connection.inputStream
                            val errorBody = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                            throw IllegalStateException("API Error ($responseCode) with model $model: $errorBody")
                        }

                        val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                        val responseJson = JSONObject(responseBody)
                        
                        val text = responseJson.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")

                        var cleanText = text.trim()
                        if (cleanText.startsWith("```")) {
                            cleanText = cleanText.substringAfter("```json")
                            if (cleanText.contains("```")) {
                                cleanText = cleanText.substringBefore("```")
                            }
                            cleanText = cleanText.trim()
                        }

                        val jsonArray = JSONArray(cleanText)
                        val holidays = mutableListOf<ParsedHoliday>()
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val name = obj.optString("name", "Holiday")
                            val date = obj.optString("date", "")
                            if (date.isNotEmpty()) {
                                holidays.add(ParsedHoliday(name = name, date = date))
                            }
                        }
                        holidays
                    } finally {
                        connection.disconnect()
                    }
                }
                return parsedHolidays
            } catch (e: Throwable) {
                e.printStackTrace()
                lastException = e
            }
        }

        throw lastException ?: IllegalStateException("All candidate models failed to parse the holiday PDF file")
    }
}

data class ParsedHoliday(val name: String, val date: String)

package com.zachvlat.instakitty.data.remote

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject

class CommentsHtmlInterceptor(baseUrl: String) : Interceptor {

    private val baseUrlClean = baseUrl.trimEnd('/')

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val url = request.url.encodedPath
        if (!url.startsWith("/p/")) return response

        val contentType = response.body?.contentType()?.toString() ?: return response
        if (!contentType.contains("text/html")) return response

        val html = response.body?.string() ?: return response

        val items = JSONArray()
        val articleRegex = Regex("<article>(.*?)</article>", RegexOption.DOT_MATCHES_ALL)
        val usernameRegex = Regex("""<a class="username" href="/([^"]+)">""")
        val textRegex = Regex("""<p class="comment-text">(.*?)</p>""")
        val imgRegex = Regex("""<img src="([^"]+)"""")
        val cursorRegex = Regex("""<a class="next-button" href="[^"]*\?cursor=([^"&]+)""")

        for (match in articleRegex.findAll(html)) {
            val article = match.groupValues[1]
            val username = usernameRegex.find(article)?.groupValues?.get(1) ?: continue
            val text = textRegex.find(article)?.groupValues?.get(1) ?: ""
            val rawImg = imgRegex.find(article)?.groupValues?.get(1) ?: ""

            val profilePicUrl = if (rawImg.startsWith("/")) "$baseUrlClean$rawImg" else rawImg

            val userObj = JSONObject().apply {
                put("username", username)
                put("profile_pic_url", profilePicUrl)
                put("profile_picture", profilePicUrl)
            }

            val commentObj = JSONObject().apply {
                put("text", text)
                put("user", userObj)
            }

            items.put(commentObj)
        }

        val endCursor = cursorRegex.find(html)?.groupValues?.get(1)

        val jsonBody = JSONObject().apply {
            put("items", items)
            if (endCursor != null) put("end_cursor", endCursor)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = jsonBody.toString().toResponseBody(mediaType)

        return response.newBuilder()
            .body(body)
            .build()
    }
}

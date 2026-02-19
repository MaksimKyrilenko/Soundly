package com.example.soundly.data.remote

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class NewPipeDownloader private constructor() : Downloader() {
    
    companion object {
        private var instance: NewPipeDownloader? = null
        
        fun getInstance(): NewPipeDownloader {
            if (instance == null) {
                instance = NewPipeDownloader()
            }
            return instance!!
        }
    }
    
    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val url = URL(request.url())
        val connection = url.openConnection() as HttpURLConnection
        
        connection.requestMethod = request.httpMethod()
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        
        // Добавляем заголовки
        request.headers().forEach { (key, values) ->
            values.forEach { value ->
                connection.addRequestProperty(key, value)
            }
        }
        
        // Добавляем User-Agent если его нет
        if (!request.headers().containsKey("User-Agent")) {
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
        }
        
        // Отправляем тело запроса если есть
        request.dataToSend()?.let { data ->
            connection.doOutput = true
            connection.outputStream.use { it.write(data) }
        }
        
        val responseCode = connection.responseCode
        val responseMessage = connection.responseMessage
        val responseHeaders = mutableMapOf<String, List<String>>()
        
        connection.headerFields.forEach { (key, values) ->
            if (key != null) {
                responseHeaders[key] = values
            }
        }
        
        val responseBody = try {
            if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
        } catch (e: Exception) {
            ""
        }
        
        return Response(
            responseCode,
            responseMessage,
            responseHeaders,
            responseBody,
            request.url()
        )
    }
}

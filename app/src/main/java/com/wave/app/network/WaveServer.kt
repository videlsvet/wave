package com.wave.app.network

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit

object WaveServer {

    // 🌍 Твой API Gateway адрес — ОБЯЗАТЕЛЬНО с "/" на конце
    private const val BASE_URL =
        "https://d5dn6r204cm4tpgfe0ms.kf69zffa.apigw.yandexcloud.net/"

    // 🔧 Логирование запросов (удобно для отладки)
    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // ⚙️ OkHttp клиент
    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    // 🧠 Настраиваем Moshi (чтобы Kotlin data class корректно конвертировался)
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // 🚀 Retrofit клиент
    val api: WaveServerApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(client)
            .build()
            .create(WaveServerApi::class.java)
    }
}

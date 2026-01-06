package com.example.soundly.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest

object SupabaseConfig {
    const val SUPABASE_URL = "https://turdivhyospjskkzpryw.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InR1cmRpdmh5b3NwanNra3pwcnl3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njc0Nzk0NTksImV4cCI6MjA4MzA1NTQ1OX0.rXIwj8dPvoZrfZZew2CQ7NnKzAneSIvV-zWVgQPgRc0"
}

object SupabaseClientProvider {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SupabaseConfig.SUPABASE_URL,
            supabaseKey = SupabaseConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
        }
    }

    val auth get() = client.auth
    val postgrest get() = client.postgrest
}

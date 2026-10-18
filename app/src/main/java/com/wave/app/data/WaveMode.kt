package com.wave.app.data

enum class WaveMode {
    COLD_START,    // Нет истории
    EXPLORATION,   // Обычный режим (30% открытий)
    FLOW           // Активное прослушивание (20% открытий)
}
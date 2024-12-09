package com.wahyurhy.ankizenbu.utils

import com.atilika.kuromoji.ipadic.Tokenizer

fun tokenizeJapaneseText(input: String): List<String> {
    val tokenizer = Tokenizer() // Langsung inisialisasi tanpa builder
    return tokenizer.tokenize(input).map { it.surface }
}
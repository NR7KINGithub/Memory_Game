package com.example.memory.models

import android.net.Uri

data class CustomGame(
    val name: String,
    val boardSize: BoardSize,
    val images: List<Uri>
)
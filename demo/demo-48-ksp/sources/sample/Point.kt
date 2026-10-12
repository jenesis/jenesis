package sample

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Point(val x: Int, val y: Int)

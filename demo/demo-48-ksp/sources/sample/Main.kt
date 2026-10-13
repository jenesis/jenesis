package sample

import com.squareup.moshi.Moshi

fun main() {
    val adapter = Moshi.Builder().build().adapter(Point::class.java)
    println(adapter.toJson(Point(1, 2)) + " written by " + adapter)
}

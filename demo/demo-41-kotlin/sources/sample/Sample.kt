package sample

class Sample {
    fun greet(): String = Greeter().prefix() + ", extended by Kotlin, compiled by Jenesis!"
}

internal fun shout(text: String): String = text.uppercase()

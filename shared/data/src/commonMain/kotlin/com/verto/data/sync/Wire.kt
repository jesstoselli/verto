package com.verto.data.sync

internal fun Enum<*>.wire(): String = name.lowercase()

internal inline fun <reified E : Enum<E>> String.toEnum(): E = enumValues<E>().first { it.wire() == this }

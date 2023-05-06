package com.unionacy.hylesaga.state

import java.security.MessageDigest
import com.google.common.io.BaseEncoding

fun transactionFamilyPrefix(): String {
    return hash("Hylesaga").substring(0, 6)
}

fun hash(input: String): String {
    val digest = MessageDigest.getInstance("SHA-512")
    digest.reset()
    digest.update(input.toByteArray())
    return BaseEncoding.base16().lowerCase().encode(digest.digest())
}

fun makeHyleAddress(hyleName: String): String {
    val HylesagaPrefix = transactionFamilyPrefix()
    val hyleAddress = hash(hyleName).substring(0, 64)
    return HylesagaPrefix + hyleAddress
}
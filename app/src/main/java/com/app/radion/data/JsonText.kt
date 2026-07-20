package com.app.radion.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** 값이 없거나 빈 문자열, 문자열 "null"이면 없는 것으로 본다(방송사 API가 셋을 섞어 쓴다). */
internal fun JsonObject.str(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

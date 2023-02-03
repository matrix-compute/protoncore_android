/*
 * HvPageLoadTotalV1.kt
 *
 * This code was generated by json-kotlin-schema-codegen - JSON Schema Code Generator
 * See https://github.com/pwall567/json-kotlin-schema-codegen
 *
 * It is not advisable to modify generated code as any modifications will be lost
 * when the generation process is re-run.
 *
 * Generated from 6d4b7f1
 * 
 */
package me.proton.core.observability.domain.entity

import kotlin.Suppress
import kotlinx.serialization.Serializable

/**
 * Loading the Verify web app inside Android's web view.
 */
@SchemaId("https://proton.me/android_core_hv_pageLoad_total_v1.schema.json")
@Suppress("ConstructorParameterNaming", "EnumNaming", "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING")
@Serializable
data class HvPageLoadTotalV1(
    val Value: Long,
    @kotlinx.serialization.SerialName("Labels") val labels: Labels
) : ObservabilityData {

    init {
        require(Value >= 1L) { "Value < minimum 1 - $Value" }
    }

    @Serializable data class Labels constructor(
        val status: Status,
        val routing: Routing
    )

    enum class Status {
        http2xx,
        http4xx,
        http5xx,
        connectionError,
        sslError
    }

    enum class Routing {
        standard,
        alternative
    }

    companion object

}
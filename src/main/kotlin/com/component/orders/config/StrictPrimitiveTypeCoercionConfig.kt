package com.component.orders.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.cfg.CoercionAction.Fail
import com.fasterxml.jackson.databind.cfg.CoercionInputShape.Boolean
import com.fasterxml.jackson.databind.cfg.CoercionInputShape.Float
import com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import kotlin.apply

@Configuration
open class StrictPrimitiveTypeCoercionConfig {
    @Bean
    @Primary
    open fun objectMapper(builder: Jackson2ObjectMapperBuilder): ObjectMapper {
        return builder.build<ObjectMapper>().apply {
            // Disable all type coercion - reject when wrong types are sent
            val inputDataTypes = listOf(Integer, Float, Boolean)
            inputDataTypes.forEach { coercionConfigDefaults().setCoercion(it, Fail) }
        }
    }
}
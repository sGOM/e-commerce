package com.example.starter

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class StarterApplication

fun main(args: Array<String>) {
	runApplication<StarterApplication>(*args)
}

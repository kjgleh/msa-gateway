package me.kjgleh.msa.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MsaGatewayApplication

fun main(args: Array<String>) {
    runApplication<MsaGatewayApplication>(*args)
}

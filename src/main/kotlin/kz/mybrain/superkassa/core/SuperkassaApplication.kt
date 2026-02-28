package kz.mybrain.superkassa.core

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SuperkassaApplication

fun main(args: Array<String>) {
    runApplication<SuperkassaApplication>(*args)
}

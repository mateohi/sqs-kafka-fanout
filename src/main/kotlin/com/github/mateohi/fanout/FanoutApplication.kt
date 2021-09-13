package com.github.mateohi.fanout

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FanoutApplication

fun main(args: Array<String>) {
	runApplication<FanoutApplication>(*args)
}

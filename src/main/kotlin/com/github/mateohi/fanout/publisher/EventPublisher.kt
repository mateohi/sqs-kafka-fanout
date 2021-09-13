package com.github.mateohi.fanout.publisher

import com.github.mateohi.fanout.dto.Event
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class EventPublisher(val kafkaTemplate: KafkaTemplate<String, Any>) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    fun publish(event: Event) {
        val topic = topicForEvent(event)
        kafkaTemplate.send(topic, event).get(10, TimeUnit.SECONDS)
        log.info("published into topic $topic event $event")
    }

    private fun topicForEvent(event: Event) = when(event.type.takeWhile { it != '.' }) {
        "accounts" -> "accounts-topic"
        "users" -> "users-topic"
        else -> throw IllegalStateException()
    }
}
package com.github.mateohi.fanout.consumer

import com.github.mateohi.fanout.dto.Event
import com.github.mateohi.fanout.publisher.EventPublisher
import org.slf4j.LoggerFactory
import org.springframework.cloud.aws.messaging.listener.SqsMessageDeletionPolicy
import org.springframework.cloud.aws.messaging.listener.annotation.SqsListener
import org.springframework.stereotype.Component

@Component
class SqsConsumer(val eventPublisher: EventPublisher) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @SqsListener(value = ["events"], deletionPolicy = SqsMessageDeletionPolicy.ON_SUCCESS)
    fun consumeEventQueue(event: Event) {
        log.info("consuming event $event")
        eventPublisher.publish(event)
    }
}
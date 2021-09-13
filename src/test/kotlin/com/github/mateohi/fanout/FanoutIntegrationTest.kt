package com.github.mateohi.fanout

import com.github.mateohi.fanout.dto.Event
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS

@Testcontainers
@SpringBootTest
class FanoutIntegrationTest {

    private val mapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    companion object {

        private val topics = listOf("accounts-topic", "users-topic")

        @Container
        val kafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.0"))

        val kafkaConsumer: Consumer<String, String> by lazy {
            val props = mapOf(
                    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                    ConsumerConfig.GROUP_ID_CONFIG to "group1",
                    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaContainer.bootstrapServers,
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            )
            DefaultKafkaConsumerFactory<String, String>(props).createConsumer().also {
                it.subscribe(topics)
            }
        }

        private val kafkaAdmin: KafkaAdmin by lazy {
            val props = mapOf(
                    AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaContainer.bootstrapServers
            )
            KafkaAdmin(props)
        }

        private fun initKafkaTopics() = topics.forEach { topic ->
            kafkaAdmin.createOrModifyTopics(NewTopic(topic, 1, 1))
        }

        @JvmStatic
        @DynamicPropertySource
        fun kafkaProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.producer.bootstrap-servers", kafkaContainer::getBootstrapServers)
        }

        @Container
        val localStack: LocalStackContainer = LocalStackContainer(DockerImageName.parse("localstack/localstack:0.12.16"))
                .withServices(SQS)
                .withEnv("DEFAULT_REGION", "us-east-1")

        val sqs: AmazonSQS by lazy {
            AmazonSQSClientBuilder.standard()
                    .withEndpointConfiguration(localStack.getEndpointConfiguration(SQS))
                    .withCredentials(localStack.defaultCredentialsProvider)
                    .build()
        }

        val eventsQueueUrl: String by lazy {
            sqs.createQueue("events").queueUrl
        }

        @JvmStatic
        @DynamicPropertySource
        fun awsProperties(registry: DynamicPropertyRegistry) {
            registry.add("cloud.aws.credentials.accessKey", localStack::getAccessKey)
            registry.add("cloud.aws.credentials.secretKey", localStack::getSecretKey)
            registry.add("cloud.aws.region.static", localStack::getRegion)
            registry.add("aws.sqs.url", this::eventsQueueUrl)
        }

        @JvmStatic
        @BeforeAll
        fun setup() {
            initKafkaTopics()
        }
    }

    @Test
    fun `test published SQS messages get consumed and published in Kafka`() {
        // given
        val event = event()

        // when
        publishMessage(event)
        SECONDS.sleep(2)

        // then
        val messageCount = sqs.receiveMessage(eventsQueueUrl).messages.stream().count()
        val eventCount = kafkaConsumer.poll(Duration.ofSeconds(2)).count()

        assertThat(messageCount).isEqualTo(0)
        assertThat(eventCount).isEqualTo(1)
    }

    @Test
    fun `test incorrect published SQS messages do not get consumed or published in Kafka`() {
        // given
        val event = event(eventType = "wrong.something", payload = "payload for wrong message")

        // when
        publishMessage(event)
        SECONDS.sleep(2)

        // then
        val messageCount = sqs.receiveMessage(eventsQueueUrl).messages.stream().count()
        val eventCount = kafkaConsumer.poll(Duration.ofSeconds(2)).count()

        assertThat(messageCount).isEqualTo(1)
        assertThat(eventCount).isEqualTo(0)

        // cleanup
        sqs.receiveMessage(eventsQueueUrl).messages.first().receiptHandle.let {
            sqs.deleteMessage(eventsQueueUrl, it)
        }
    }

    private fun publishMessage(event: Event) =
            sqs.sendMessage(eventsQueueUrl, mapper.writeValueAsString(event))

    private fun event(
            payload: String = "payload",
            eventType: String = "users"
    ) = Event(
            id = UUID.randomUUID().toString(),
            type = "$eventType.subtype",
            timestamp = Instant.now(),
            payload = payload,
    )
}
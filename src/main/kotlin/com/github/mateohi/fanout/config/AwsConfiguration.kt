package com.github.mateohi.fanout.config

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.converter.MessageConverter

@Configuration
class AwsConfiguration(private val awsProperties: AwsProperties) {

    @Bean
    @Primary
    fun amazonSQSAsyncClient(): AmazonSQSAsync =
            AmazonSQSAsyncClientBuilder.standard()
                    .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(awsProperties.accessKey, awsProperties.secretKey)))
                    .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(awsProperties.url, awsProperties.region))
                    .build()

    @Bean
    fun messageConverter(objectMapper: ObjectMapper): MessageConverter = MappingJackson2MessageConverter().apply {
        this.objectMapper = objectMapper
    }
}
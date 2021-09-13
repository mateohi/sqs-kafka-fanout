package com.github.mateohi.fanout.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class AwsProperties(
        @Value("\${cloud.aws.credentials.accessKey}")
        val accessKey: String,
        @Value("\${cloud.aws.credentials.secretKey}")
        val secretKey: String,
        @Value("\${cloud.aws.region.static}")
        val region: String,
        @Value("\${aws.sqs.url}")
        val url: String
)
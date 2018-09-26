/*
 * Copyright  2018 Charlie Black
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package com.github.sigeode;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.geode.cache.asyncqueue.AsyncEvent;
import org.apache.geode.internal.logging.LogService;
import org.apache.geode.pdx.JSONFormatter;
import org.apache.geode.pdx.PdxInstance;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.aws.outbound.S3MessageHandler;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import java.util.List;

@Configuration
@EnableIntegration
public class AsyncS3Configuration {
    private static Logger logger = LogService.getLogger();


    @Bean
    AmazonS3 amazonS3(@Value("${sigeode.awsregion:us-east-1}") String region) {
        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard();
        builder.setRegion(region);
        return builder.build();
    }

    @Bean
    Expression keyExpression() {
        ExpressionParser parser = new SpelExpressionParser();
        StringBuilder buffer = new StringBuilder("'spring-integration-example/'")
                .append(" + T(java.util.Calendar).getInstance().get(1) ")
                .append(" + T(java.lang.String).format('%02d', T(java.util.Calendar).getInstance().get(2) + 1) ")
                .append(" + T(java.lang.String).format('%02d', T(java.util.Calendar).getInstance().get(5)) ")
                .append(" + '/' + T(java.lang.System).currentTimeMillis() + '_' + T(java.util.UUID).randomUUID() + '.json'");
        Expression exp = parser.parseExpression(buffer.toString());
        return exp;
    }

    @Bean
    MessageHandler s3UploadMessageHandler(AmazonS3 amazonS3,
                                          Expression keyExpression) {
        S3MessageHandler messageHandler = new S3MessageHandler(amazonS3, "apache-geode");
        messageHandler.setCommand(S3MessageHandler.Command.UPLOAD);

        messageHandler.setKeyExpression(keyExpression);
        return messageHandler;
    }

    @Bean
    MessageChannel inputChannel() {
        PublishSubscribeChannel publishSubscribeChannel = new PublishSubscribeChannel();
        return publishSubscribeChannel;
    }

    @Bean
    IntegrationFlow outputToS3(MessageChannel inputChannel,
                               MessageHandler s3UploadMessageHandler) {

        IntegrationFlow integrationFlow = IntegrationFlows.from(inputChannel)
                // transform the AsyncEvent list to a tuple
                // [region name, operation, key, value]
                .transform((List<AsyncEvent> events) -> asyncEventToJson(events))
                //The S3 uploader likes certain data types
                .transform((String s) -> s.getBytes())
                //Upload to S3
                .handle(s3UploadMessageHandler)
                .get();
        return integrationFlow;
    }

    //TODO: make a StdSerializer for PDX then we don't need this method
    private String asyncEventToJson(List<AsyncEvent> events) {
        ObjectMapper objectMapper = new ObjectMapper();
        StringBuilder sb = new StringBuilder("[");
        try {
            boolean firstTime = true;
            for (AsyncEvent asyncEvent : events) {
                if (firstTime) {
                    firstTime = false;
                } else {
                    sb.append(",");
                }
                // Format is [ region name, operation, key, value ]
                sb.append("[\"" + asyncEvent.getRegion().getName() + "\",");
                sb.append("\"" + asyncEvent.getOperation().toString() + "\",");
                sb.append(objectMapper.writeValueAsString(asyncEvent.getKey()) + ",");
                sb.append(transformValue(objectMapper, asyncEvent.getDeserializedValue()));
                sb.append("]");
            }
            sb.append("]");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return sb.toString();
    }

    private Object transformValue(ObjectMapper objectMapper, Object value) throws JsonProcessingException {
        if (value != null) {
            if (value instanceof PdxInstance) {
                value = JSONFormatter.toJSON((PdxInstance) value);
            } else {
                value = objectMapper.writeValueAsString(value);
            }
        } else {
            value = "null";
        }
        return value;
    }
}

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

import org.apache.geode.cache.asyncqueue.AsyncEvent;
import org.apache.geode.cache.asyncqueue.AsyncEventListener;
import org.apache.geode.internal.logging.LogService;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;

import java.util.List;
import java.util.Properties;

public class AsyncS3Sink implements AsyncEventListener {
    private static Logger logger = LogService.getLogger();

    private ConfigurableApplicationContext ctx;
    private MessageChannel messageChannel;

    @Override
    public boolean processEvents(List<AsyncEvent> events) {
        messageChannel.send(MessageBuilder.withPayload(events).build());
        return true;
    }

    @Override
    public void close() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Override
    public void init(Properties properties) {
        try {
            PropertyPlaceholderConfigurer propConfig = new PropertyPlaceholderConfigurer();
            propConfig.setProperties(properties);

            AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
            ctx.addBeanFactoryPostProcessor(propConfig);
            ctx.register(AsyncS3Configuration.class);
            ctx.refresh();
            messageChannel = (MessageChannel) ctx.getBean("inputChannel");
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }
}

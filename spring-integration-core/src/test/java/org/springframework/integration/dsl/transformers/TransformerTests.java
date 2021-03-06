/*
 * Copyright 2017-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.dsl.transformers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.MessageRejectedException;
import org.springframework.integration.annotation.Transformer;
import org.springframework.integration.channel.FixedSubscriberChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.codec.Codec;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.dsl.Transformers;
import org.springframework.integration.handler.advice.AbstractRequestHandlerAdvice;
import org.springframework.integration.handler.advice.IdempotentReceiverInterceptor;
import org.springframework.integration.selector.MetadataStoreSelector;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author Artem Bilan
 * @author Ian Bondoc
 * @author Gary Russell
 *
 * @since 5.0
 */
@RunWith(SpringRunner.class)
@DirtiesContext
public class TransformerTests {

	@Autowired
	@Qualifier("enricherInput")
	private FixedSubscriberChannel enricherInput;

	@Autowired
	@Qualifier("enricherInput2")
	private FixedSubscriberChannel enricherInput2;

	@Autowired
	@Qualifier("enricherInput3")
	private FixedSubscriberChannel enricherInput3;

	@Autowired
	@Qualifier("enricherErrorChannel")
	private PollableChannel enricherErrorChannel;


	@Test
	public void testContentEnricher() {
		QueueChannel replyChannel = new QueueChannel();
		Message<?> message = MessageBuilder.withPayload(new TestPojo("Bar"))
				.setHeader(MessageHeaders.REPLY_CHANNEL, replyChannel)
				.build();
		this.enricherInput.send(message);
		Message<?> receive = replyChannel.receive(5000);
		assertNotNull(receive);
		assertEquals("Bar Bar", receive.getHeaders().get("foo"));
		Object payload = receive.getPayload();
		assertThat(payload, instanceOf(TestPojo.class));
		TestPojo result = (TestPojo) payload;
		assertEquals("Bar Bar", result.getName());
		assertNotNull(result.getDate());
		assertThat(new Date(), Matchers.greaterThanOrEqualTo(result.getDate()));

		this.enricherInput.send(new GenericMessage<>(new TestPojo("junk")));

		Message<?> errorMessage = this.enricherErrorChannel.receive(10_000);
		assertNotNull(errorMessage);
	}

	@Test
	public void testContentEnricher2() {
		QueueChannel replyChannel = new QueueChannel();
		Message<?> message = MessageBuilder.withPayload(new TestPojo("Bar"))
				.setHeader(MessageHeaders.REPLY_CHANNEL, replyChannel)
				.build();
		this.enricherInput2.send(message);
		Message<?> receive = replyChannel.receive(5000);
		assertNotNull(receive);
		assertNull(receive.getHeaders().get("foo"));
		Object payload = receive.getPayload();
		assertThat(payload, instanceOf(TestPojo.class));
		TestPojo result = (TestPojo) payload;
		assertEquals("Bar Bar", result.getName());
		assertNotNull(result.getDate());
		assertThat(new Date(), Matchers.greaterThanOrEqualTo(result.getDate()));
	}

	@Test
	public void testContentEnricher3() {
		QueueChannel replyChannel = new QueueChannel();
		Message<?> message = MessageBuilder.withPayload(new TestPojo("Bar"))
				.setHeader(MessageHeaders.REPLY_CHANNEL, replyChannel)
				.build();
		this.enricherInput3.send(message);
		Message<?> receive = replyChannel.receive(5000);
		assertNotNull(receive);
		assertEquals("Bar Bar", receive.getHeaders().get("foo"));
		Object payload = receive.getPayload();
		assertThat(payload, instanceOf(TestPojo.class));
		TestPojo result = (TestPojo) payload;
		assertEquals("Bar", result.getName());
		assertNull(result.getDate());
	}

	@Autowired
	@Qualifier("replyProducingSubFlowEnricher.input")
	private MessageChannel replyProducingSubFlowEnricherInput;

	@Autowired
	@Qualifier("terminatingSubFlowEnricher.input")
	private MessageChannel terminatingSubFlowEnricherInput;

	@Autowired
	@Qualifier("subFlowTestReplyChannel")
	private PollableChannel subFlowTestReplyChannel;

	@Test
	public void testSubFlowContentEnricher() {
		this.replyProducingSubFlowEnricherInput.send(MessageBuilder.withPayload(new TestPojo("Bar")).build());
		Message<?> receive = this.subFlowTestReplyChannel.receive(5000);
		assertNotNull(receive);
		assertEquals("Foo Bar (Reply Producing)", receive.getHeaders().get("foo"));
		Object payload = receive.getPayload();
		assertThat(payload, instanceOf(TestPojo.class));
		TestPojo result = (TestPojo) payload;
		assertThat(result.getName(), is("Foo Bar (Reply Producing)"));

		this.terminatingSubFlowEnricherInput.send(MessageBuilder.withPayload(new TestPojo("Bar")).build());
		receive = this.subFlowTestReplyChannel.receive(5000);
		assertNotNull(receive);
		assertEquals("Foo Bar (Terminating)", receive.getHeaders().get("foo"));
		payload = receive.getPayload();
		assertThat(payload, instanceOf(TestPojo.class));
		result = (TestPojo) payload;
		assertThat(result.getName(), is("Foo Bar (Terminating)"));
	}

	@Autowired
	@Qualifier("encodingFlow.input")
	private MessageChannel encodingFlowInput;

	@Autowired
	@Qualifier("decodingFlow.input")
	private MessageChannel decodingFlowInput;

	@Autowired
	@Qualifier("codecReplyChannel")
	private PollableChannel codecReplyChannel;

	@Test
	public void testCodec() throws Exception {
		this.encodingFlowInput.send(new GenericMessage<>("bar"));
		Message<?> receive = this.codecReplyChannel.receive(10000);
		assertNotNull(receive);
		assertThat(receive.getPayload(), instanceOf(byte[].class));
		byte[] transformed = (byte[]) receive.getPayload();
		assertArrayEquals("foo".getBytes(), transformed);

		this.decodingFlowInput.send(new GenericMessage<>(transformed));
		receive = this.codecReplyChannel.receive(10000);
		assertNotNull(receive);
		assertEquals(42, receive.getPayload());
	}


	@Autowired
	@Qualifier("pojoTransformFlow.input")
	private MessageChannel pojoTransformFlowInput;

	@Autowired
	private PollableChannel idempotentDiscardChannel;

	@Autowired
	private PollableChannel adviceChannel;

	@Test
	public void transformWithHeader() {
		QueueChannel replyChannel = new QueueChannel();
		Message<?> message = MessageBuilder.withPayload("Foo")
				.setReplyChannel(replyChannel)
				.build();
		this.pojoTransformFlowInput.send(message);
		Message<?> receive = replyChannel.receive(10000);
		assertNotNull(receive);
		assertEquals("FooBar", receive.getPayload());

		try {
			this.pojoTransformFlowInput.send(message);
			fail("MessageRejectedException expected");
		}
		catch (Exception e) {
			assertThat(e, instanceOf(MessageRejectedException.class));
			assertThat(e.getMessage(), containsString("IdempotentReceiver"));
			assertThat(e.getMessage(), containsString("rejected duplicate Message"));
		}

		assertNotNull(this.idempotentDiscardChannel.receive(10000));
		assertNotNull(this.adviceChannel.receive(10000));
	}

	@Configuration
	@EnableIntegration
	public static class ContextConfiguration {

		@Bean
		public PollableChannel enricherErrorChannel() {
			return new QueueChannel();
		}

		@Bean
		public IntegrationFlow enricherFlow() {
			return IntegrationFlows.from("enricherInput", true)
					.enrich(e -> e.requestChannel("enrichChannel")
							.errorChannel(enricherErrorChannel())
							.requestPayloadExpression("payload")
							.shouldClonePayload(false)
							.propertyExpression("name", "payload['name']")
							.propertyFunction("date", m -> new Date())
							.headerExpression("foo", "payload['name']")
					)
					.get();
		}

		@Bean
		public IntegrationFlow enricherFlow2() {
			return IntegrationFlows.from("enricherInput2", true)
					.enrich(e -> e.requestChannel("enrichChannel")
							.requestPayloadExpression("payload")
							.shouldClonePayload(false)
							.propertyExpression("name", "payload['name']")
							.propertyExpression("date", "new java.util.Date()")
					)
					.get();
		}

		@Bean
		public IntegrationFlow enricherFlow3() {
			return IntegrationFlows.from("enricherInput3", true)
					.enrich(e -> e.requestChannel("enrichChannel")
							.requestPayload(Message::getPayload)
							.shouldClonePayload(false)
							.<Map<String, String>>headerFunction("foo", m -> m.getPayload().get("name")))
					.get();
		}

		@Bean
		public IntegrationFlow enrichFlow() {
			return IntegrationFlows.from("enrichChannel")
					.<TestPojo, Map<?, ?>>transform(p -> {
						if ("junk".equals(p.getName())) {
							throw new RuntimeException("intentional");
						}
						return Collections.singletonMap("name", p.getName() + " Bar");
					})
					.get();
		}

		@Bean
		public PollableChannel receivedChannel() {
			return new QueueChannel();
		}

		@Bean
		public PollableChannel codecReplyChannel() {
			return new QueueChannel();
		}

		@Bean
		public IntegrationFlow encodingFlow() {
			return f -> f
					.transform(Transformers.encoding(new MyCodec()))
					.channel("codecReplyChannel");
		}

		@Bean
		public IntegrationFlow decodingFlow() {
			return f -> f
					.transform(Transformers.decoding(new MyCodec(), m -> Integer.class))
					.channel("codecReplyChannel");
		}

		@Bean
		public IntegrationFlow pojoTransformFlow() {
			return f -> f
					.enrichHeaders(h -> h
							.header("Foo", "Bar")
							.advice(idempotentReceiverInterceptor(), requestHandlerAdvice()))
					.transform(new PojoTransformer());
		}

		@Bean
		public PollableChannel idempotentDiscardChannel() {
			return new QueueChannel();
		}

		@Bean
		public IdempotentReceiverInterceptor idempotentReceiverInterceptor() {
			IdempotentReceiverInterceptor idempotentReceiverInterceptor =
					new IdempotentReceiverInterceptor(new MetadataStoreSelector(m -> m.getPayload().toString()));
			idempotentReceiverInterceptor.setDiscardChannelName("idempotentDiscardChannel");
			idempotentReceiverInterceptor.setThrowExceptionOnRejection(true);
			return idempotentReceiverInterceptor;
		}

		@Bean
		public AbstractRequestHandlerAdvice requestHandlerAdvice() {
			return new AbstractRequestHandlerAdvice() {

				@Override
				protected Object doInvoke(ExecutionCallback callback, Object target, Message<?> message)
						throws Exception {

					adviceChannel().send(message);
					return callback.execute();
				}

			};
		}

		@Bean
		public PollableChannel adviceChannel() {
			return new QueueChannel();
		}

		@Bean
		public PollableChannel subFlowTestReplyChannel() {
			return new QueueChannel();
		}

		@Bean
		public IntegrationFlow replyProducingSubFlowEnricher(SomeService someService) {
			return f -> f
					.enrich(e -> e.<TestPojo>requestPayload(p -> p.getPayload().getName())
							.requestSubFlow(sf -> sf
									.<String>handle((p, h) -> someService.someServiceMethod(p)))
							.<String>headerFunction("foo", Message::getPayload)
							.propertyFunction("name", Message::getPayload))
					.channel("subFlowTestReplyChannel");
		}

		@Bean
		public MessageChannel enricherReplyChannel() {
			return MessageChannels.direct().get();
		}

		@Bean
		public IntegrationFlow terminatingSubFlowEnricher(SomeService someService) {
			return f -> f
					.enrich(e -> e.<TestPojo>requestPayload(p -> p.getPayload().getName())
							.requestSubFlow(sf -> sf
									.handle(someService::aTerminatingServiceMethod))
							.replyChannel("enricherReplyChannel")
							.<String>headerFunction("foo", Message::getPayload)
							.propertyFunction("name", Message::getPayload))
					.channel("subFlowTestReplyChannel");
		}

		@Bean
		public SomeService someService() {
			return new SomeService();
		}

	}

	private static final class TestPojo {

		private String name;

		private Date date;

		private TestPojo(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		@SuppressWarnings("unused")
		public void setName(String name) {
			this.name = name;
		}

		public Date getDate() {
			return date;
		}

		@SuppressWarnings("unused")
		public void setDate(Date date) {
			this.date = date;
		}

	}

	public static class MyCodec implements Codec {

		@Override
		public void encode(Object object, OutputStream outputStream) throws IOException {
		}

		@Override
		public byte[] encode(Object object) throws IOException {
			return "foo".getBytes();
		}

		@Override
		public <T> T decode(InputStream inputStream, Class<T> type) throws IOException {
			return null;
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T> T decode(byte[] bytes, Class<T> type) throws IOException {
			return (T) (type.equals(String.class) ? new String(bytes) :
					type.equals(Integer.class) ? Integer.valueOf(42) : Integer.valueOf(43));
		}

	}

	public static class PojoTransformer {

		@Transformer
		public String transform(String payload, @Header("Foo") String header) {
			return payload + header;
		}

	}

	public static class SomeService {

		@Autowired
		@Qualifier("enricherReplyChannel")
		public MessageChannel enricherReplyChannel;

		public String someServiceMethod(String value) {
			return "Foo ".concat(value).concat(" (Reply Producing)");
		}

		public void aTerminatingServiceMethod(Message<?> message) {
			String payload = "Foo ".concat(message.getPayload().toString()).concat(" (Terminating)");
			enricherReplyChannel.send(MessageBuilder.withPayload(payload).copyHeaders(message.getHeaders()).build());
		}

	}

}

package com.booking.config;

// ── IMPORTS ────────────────────────────────────────────────────────────────

import org.springframework.amqp.core.Binding;
// A Binding is the rule: "route messages with routing key X to queue Y"

import org.springframework.amqp.core.BindingBuilder;
// Helper class for building Binding objects with readable syntax

import org.springframework.amqp.core.DirectExchange;
// A type of exchange that routes messages to queues based on exact routing key match
// "Direct" = routing key must match exactly (vs. "Topic" which allows wildcards)

import org.springframework.amqp.core.Queue;
// A queue is a buffer that holds messages until a consumer processes them

import org.springframework.amqp.core.QueueBuilder;
// Helper class for building Queue objects

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
// Spring creates this automatically based on application.yml RabbitMQ settings
// (host, port, username, password)

import org.springframework.amqp.rabbit.core.RabbitTemplate;
// The main object for SENDING messages to RabbitMQ
// BookingService will use this: rabbitTemplate.convertAndSend(...)

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
// Converts Java objects to JSON when sending messages
// and JSON back to Java objects when receiving

import org.springframework.amqp.support.converter.MessageConverter;
// Interface that Jackson2JsonMessageConverter implements

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


// ── CLASS ANNOTATION ───────────────────────────────────────────────────────

@Configuration
public class RabbitMQConfig {


    // ── CONSTANTS ─────────────────────────────────────────────────────────
    // We define all names as constants here
    // WHY: if we hardcode "booking.exchange" as a String in 5 different files,
    // a typo in one file causes a silent bug that's hard to find
    // With constants: if you mistype the constant name, Java catches it at compile time

    public static final String BOOKING_EXCHANGE = "booking.exchange";
    // The exchange name in RabbitMQ
    // BookingService will send to this exchange

    public static final String QUEUE_BOOKING_CONFIRMED = "booking.confirmed.queue";
    // Queue name for successful booking messages
    // EmailConsumer will listen to this queue

    public static final String QUEUE_BOOKING_CANCELLED = "booking.cancelled.queue";
    // Queue name for cancellation messages

    public static final String ROUTING_KEY_CONFIRMED = "booking.confirmed";
    // The label on a "booking confirmed" message
    // Exchange uses this to route to QUEUE_BOOKING_CONFIRMED

    public static final String ROUTING_KEY_CANCELLED = "booking.cancelled";
    // The label on a "booking cancelled" message


    // ── BEAN 1: Exchange ──────────────────────────────────────────────────
    // The exchange is the message router — it receives all messages
    // and decides which queue each message goes to

    @Bean
    public DirectExchange bookingExchange() {
        return new DirectExchange(
            BOOKING_EXCHANGE,   // name: "booking.exchange"
            true,               // durable: survives RabbitMQ restart
            false               // autoDelete: don't delete when unused
        );
        // DirectExchange routing logic:
        //   Message arrives with routing key "booking.confirmed"
        //   Exchange checks its bindings
        //   Finds: "booking.confirmed" → booking.confirmed.queue
        //   Delivers message to that queue
        //
        // WHY durable = true?
        // If RabbitMQ restarts (server reboot, crash), durable exchanges
        // are recreated automatically. Non-durable exchanges disappear
        // and messages sent to them would be lost.
    }


    // ── BEAN 2: Queue for confirmed bookings ──────────────────────────────

    @Bean
    public Queue bookingConfirmedQueue() {
        return QueueBuilder
            .durable(QUEUE_BOOKING_CONFIRMED)
            // durable = this queue survives RabbitMQ restarts
            // Messages already in the queue are NOT lost if RabbitMQ restarts
            .build();
        // What happens when a message arrives here:
        // 1. Message sits in the queue
        // 2. EmailNotificationConsumer (Phase 6) is listening
        // 3. Consumer picks up the message
        // 4. Consumer sends the confirmation email
        // 5. Consumer acknowledges the message (tells RabbitMQ "I processed it")
        // 6. RabbitMQ removes the message from the queue
        //
        // IF the consumer crashes while processing:
        // The message is returned to the queue (not acknowledged)
        // Another consumer instance picks it up and tries again
        // This is the "at-least-once delivery" guarantee
    }


    // ── BEAN 3: Queue for cancelled bookings ──────────────────────────────

    @Bean
    public Queue bookingCancelledQueue() {
        return QueueBuilder
            .durable(QUEUE_BOOKING_CANCELLED)
            .build();
        // Same concept as confirmed queue, but for cancellations
        // A different consumer will send cancellation emails
    }


    // ── BEAN 4: Binding — connect confirmed queue to exchange ──────────────

    @Bean
    public Binding bookingConfirmedBinding() {
        return BindingBuilder
            .bind(bookingConfirmedQueue())
            // "take this queue..."

            .to(bookingExchange())
            // "...connect it to this exchange..."

            .with(ROUTING_KEY_CONFIRMED);
            // "...and deliver messages that have THIS routing key to this queue"
            //
            // Full rule: messages sent to "booking.exchange" with routing key
            // "booking.confirmed" → go to "booking.confirmed.queue"
            //
            // Without this binding:
            //   BookingService sends message to exchange ✓
            //   Exchange has no rule for this routing key ✗
            //   Message is dropped silently — customer never gets email!
    }


    // ── BEAN 5: Binding — connect cancelled queue to exchange ──────────────

    @Bean
    public Binding bookingCancelledBinding() {
        return BindingBuilder
            .bind(bookingCancelledQueue())
            .to(bookingExchange())
            .with(ROUTING_KEY_CANCELLED);
        // Messages with routing key "booking.cancelled" → "booking.cancelled.queue"
    }


    // ── BEAN 6: Message Converter ──────────────────────────────────────────
    // This defines HOW Java objects are converted to bytes for transmission

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
        // Jackson2JsonMessageConverter uses the Jackson library (same as Spring MVC)
        // to convert Java objects to JSON strings, then to bytes
        //
        // SENDING:   BookingConfirmedEvent object → {"bookingId":"abc","email":"..."} → bytes
        // RECEIVING: bytes → {"bookingId":"abc","email":"..."} → BookingConfirmedEvent object
        //
        // WHY JSON instead of Java binary serialization (the default)?
        // 1. Human readable — open RabbitMQ management UI, see actual message content
        // 2. Cross-language — Python, Node.js consumers could read these messages
        // 3. Version safe — adding new fields doesn't break existing consumers
        // 4. Debuggable — you can manually inspect stuck messages in the queue
    }


    // ── BEAN 7: RabbitTemplate ────────────────────────────────────────────
    // The main object used to SEND messages to RabbitMQ

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        // Spring injects the ConnectionFactory (created from application.yml config)
        // ConnectionFactory contains: host=rabbitmq, port=5672, user=rabbit_user

        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        // Create the template with the connection details

        template.setMessageConverter(jsonMessageConverter());
        // Tell the template: use JSON for all messages
        // Now when BookingService calls:
        //   rabbitTemplate.convertAndSend(exchange, routingKey, myObject)
        // The template automatically:
        //   1. Converts myObject to JSON using jsonMessageConverter
        //   2. Sends the JSON bytes to the exchange
        //   3. Exchange routes to the correct queue
        //
        // WITHOUT setMessageConverter:
        //   Messages would be Java binary serialization
        //   Unreadable in management UI, fragile, hard to debug

        return template;
        // BookingService will declare:
        //   private final RabbitTemplate rabbitTemplate;
        // Spring injects this fully configured template
    }
}
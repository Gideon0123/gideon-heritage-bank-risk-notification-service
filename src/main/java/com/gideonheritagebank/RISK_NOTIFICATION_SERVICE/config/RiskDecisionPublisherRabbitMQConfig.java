package com.gideonheritagebank.RISK_NOTIFICATION_SERVICE.config;
import org.springframework.amqp.core.TopicExchange;import org.springframework.beans.factory.annotation.Value;import org.springframework.context.annotation.Bean;import org.springframework.context.annotation.Configuration;
@Configuration public class RiskDecisionPublisherRabbitMQConfig{@Bean public TopicExchange riskDecisionExchange(@Value("${risk.outbox.exchange:bank.risk-decisions.exchange}")String name){return new TopicExchange(name,true,false);}}

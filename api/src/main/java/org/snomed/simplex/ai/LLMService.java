package org.snomed.simplex.ai;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.simplex.service.LlmUsageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;

@Service
public class LLMService {

	private final ConfiguredChatModel fastModel;
	private final ConfiguredChatModel goodModel;
	private final LlmUsageService llmUsageService;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	public LLMService(
			@Value("${openai.api-key}") String apiKey,
			@Value("${openai.fast.model-name}") String fastModelName,
			@Value("${openai.fast.service-tier:}") String fastServiceTier,
			@Value("${openai.fast.billing-model-name:}") String fastBillingModelName,
			@Value("${openai.good.model-name}") String goodModelName,
			@Value("${openai.good.service-tier:}") String goodServiceTier,
			@Value("${openai.good.billing-model-name:}") String goodBillingModelName,
			LlmUsageService llmUsageService) {

		this.llmUsageService = llmUsageService;
		fastModel = configureModel(
				getOpenAiChatModel(apiKey, fastModelName, fastServiceTier),
				fastModelName,
				billingModelName(fastBillingModelName, fastModelName));
		goodModel = configureModel(
				getOpenAiChatModel(apiKey, goodModelName, goodServiceTier),
				goodModelName,
				billingModelName(goodBillingModelName, goodModelName));
	}

	LLMService(LlmUsageService llmUsageService, ConfiguredChatModel fastModel, ConfiguredChatModel goodModel) {
		this.llmUsageService = llmUsageService;
		this.fastModel = fastModel;
		this.goodModel = goodModel;
	}

	private static OpenAiChatModel getOpenAiChatModel(String apiKey, String modelName, String serviceTier) {
		OpenAiChatModel.OpenAiChatModelBuilder modelBuilder = OpenAiChatModel.builder()
				.apiKey(apiKey)
				.modelName(modelName)
				.timeout(Duration.ofMinutes(2));
		if (serviceTier != null && !serviceTier.isBlank()) {
			modelBuilder.serviceTier(serviceTier);
		}
		if (!modelName.startsWith("gpt-5")) {
			modelBuilder
					.maxTokens(500)
					.temperature(0.0);
		}

		return modelBuilder.build();
	}

	private static ConfiguredChatModel configureModel(ChatModel model, String configuredModelName, String billingModelName) {
		String provider = providerSlug(model.provider());
		return new ConfiguredChatModel(model, configuredModelName, billingModelName, provider);
	}

	private static String billingModelName(String configuredBillingModelName, String modelName) {
		if (configuredBillingModelName != null && !configuredBillingModelName.isBlank()) {
			return configuredBillingModelName;
		}
		return modelName;
	}

	public String chat(String message, boolean fast, LlmCallContext context) {
		ConfiguredChatModel configuredModel = getChatModel(fast);
		long start = new Date().getTime();
		ChatResponse response = configuredModel.model().chat(ChatRequest.builder()
				.messages(UserMessage.from(message))
				.build());
		String text = response.aiMessage().text();
		text = text.replace("```json", "").replace("```", "");
		long duration = new Date().getTime() - start;

		recordUsage(configuredModel, response, context);

		if (logger.isInfoEnabled()) {
			logger.info("Chat took {}s using model {}\nRequest:\n{}\nResponse:\n{}",
				((float) duration) / 1000, resolveModelName(configuredModel, response), message, text);
		}
		return text;
	}

	private void recordUsage(ConfiguredChatModel configuredModel, ChatResponse response, LlmCallContext context) {
		if (context == null || context.codesystem() == null || context.codesystem().isBlank()) {
			return;
		}
		TokenUsage usage = response.tokenUsage();
		int inputTokens = usage != null && usage.inputTokenCount() != null ? usage.inputTokenCount() : 0;
		int outputTokens = usage != null && usage.outputTokenCount() != null ? usage.outputTokenCount() : 0;
		llmUsageService.recordUsage(new LlmUsageRecord(
				context.codesystem(),
				configuredModel.billingModelName(),
				configuredModel.provider(),
				inputTokens,
				outputTokens,
				context.conceptsTranslated()
		));
	}

	private static String resolveModelName(ConfiguredChatModel configuredModel, ChatResponse response) {
		if (response.modelName() != null && !response.modelName().isBlank()) {
			return response.modelName();
		}
		return configuredModel.modelName();
	}

	private static String providerSlug(ModelProvider provider) {
		if (provider == null) {
			return "unknown";
		}
		return provider.name().toLowerCase().replace("_", "");
	}

	private ConfiguredChatModel getChatModel(boolean fast) {
		return fast ? fastModel : goodModel;
	}

	record ConfiguredChatModel(ChatModel model, String modelName, String billingModelName, String provider) {
	}
}

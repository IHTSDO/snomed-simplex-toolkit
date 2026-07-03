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

	public LLMService(
			@Value("${openai.api-key}") String apiKey,
			@Value("${openai.fast.model-name}") String fastModelName,
			@Value("${openai.good.model-name}") String goodModelName,
			LlmUsageService llmUsageService) {

		this.llmUsageService = llmUsageService;
		fastModel = configureModel(getOpenAiChatModel(apiKey, fastModelName), fastModelName);
		goodModel = configureModel(getOpenAiChatModel(apiKey, goodModelName), goodModelName);
	}

	private static OpenAiChatModel getOpenAiChatModel(String apiKey, String modelName) {
		OpenAiChatModel.OpenAiChatModelBuilder modelBuilder = OpenAiChatModel.builder()
				.apiKey(apiKey)
				.modelName(modelName)
				.timeout(Duration.ofMinutes(2));
		if (!modelName.startsWith("gpt-5")) {
			modelBuilder
					.maxTokens(500)
					.temperature(0.0);
		}

		return modelBuilder.build();
	}

	private static ConfiguredChatModel configureModel(ChatModel model, String configuredModelName) {
		String provider = providerSlug(model.provider());
		return new ConfiguredChatModel(model, configuredModelName, provider);
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
				resolveModelName(configuredModel, response),
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

	private record ConfiguredChatModel(ChatModel model, String modelName, String provider) {
	}
}

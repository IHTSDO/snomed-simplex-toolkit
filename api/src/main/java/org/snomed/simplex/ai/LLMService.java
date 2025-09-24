package org.snomed.simplex.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class LLMService {

	private final ChatModel fastModel;
	private final ChatModel goodModel;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public LLMService(
		@Value("${openai.api-key}") String apiKey,
		@Value("${openai.fast.model-name}") String fastModelName,
		@Value("${openai.good.model-name}") String goodModelName) {

		fastModel = getOpenAiChatModel(apiKey, fastModelName);
		goodModel = getOpenAiChatModel(apiKey, goodModelName);
	}

	private static OpenAiChatModel getOpenAiChatModel(String apiKey, String fastModelName) {
		OpenAiChatModel.OpenAiChatModelBuilder modelBuilder = OpenAiChatModel.builder()
			.apiKey(apiKey)
			.modelName(fastModelName);
		if (!fastModelName.startsWith("gpt-5")) {
			modelBuilder
				.maxTokens(500)
				.temperature(0.0);      // makes it deterministic, often faster
		}

		return modelBuilder.build();
	}

	public String chat(String message, boolean fast) {
		long start = new Date().getTime();
		String response = getChatModel(fast).chat(message);
		// Strip any json wrapper
		response = response.replace("```json", "").replace("```", "");
		long duration = new Date().getTime() - start;
		logger.info("Chat took {}s\nRequest:\n{}\nResponse:\n{}", ((float) duration) / 1000, message, response);
		return response;
	}

	private ChatModel getChatModel(boolean fast) {
		return fast ? fastModel : goodModel;
	}
}

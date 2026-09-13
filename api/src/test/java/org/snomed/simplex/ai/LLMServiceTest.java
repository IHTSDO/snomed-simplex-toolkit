package org.snomed.simplex.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.snomed.simplex.service.LlmUsageService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LLMServiceTest {

	@Test
	void chatRecordsConfiguredBillingModelNameForFastTier() {
		LlmUsageService llmUsageService = mock(LlmUsageService.class);
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse("gpt-5.6-terra-2026-07-01", 130, 60));

		LLMService service = new LLMService(
				llmUsageService,
				new LLMService.ConfiguredChatModel(chatModel, "gpt-5.6-terra", "gpt-5.6-terra-fast", "openai"),
				new LLMService.ConfiguredChatModel(chatModel, "gpt-5.6-terra", "gpt-5.6-terra", "openai"));

		service.chat("translate terms", true, new LlmCallContext("SNOMEDCT-ES", 2));

		ArgumentCaptor<LlmUsageRecord> usageCaptor = ArgumentCaptor.forClass(LlmUsageRecord.class);
		verify(llmUsageService).recordUsage(usageCaptor.capture());
		assertEquals("gpt-5.6-terra-fast", usageCaptor.getValue().model());
		assertEquals(130, usageCaptor.getValue().inputTokens());
		assertEquals(60, usageCaptor.getValue().outputTokens());
	}

	@Test
	void chatRecordsConfiguredBillingModelNameForGoodTier() {
		LlmUsageService llmUsageService = mock(LlmUsageService.class);
		ChatModel chatModel = mock(ChatModel.class);
		when(chatModel.chat(any(ChatRequest.class))).thenReturn(chatResponse("gpt-5.6-terra-2026-07-01", 200, 80));

		LLMService service = new LLMService(
				llmUsageService,
				new LLMService.ConfiguredChatModel(chatModel, "gpt-5.6-terra", "gpt-5.6-terra-fast", "openai"),
				new LLMService.ConfiguredChatModel(chatModel, "gpt-5.6-terra", "gpt-5.6-terra", "openai"));

		service.chat("translate terms", false, new LlmCallContext("SNOMEDCT-ES", 5));

		ArgumentCaptor<LlmUsageRecord> usageCaptor = ArgumentCaptor.forClass(LlmUsageRecord.class);
		verify(llmUsageService).recordUsage(usageCaptor.capture());
		assertEquals("gpt-5.6-terra", usageCaptor.getValue().model());
		assertEquals(200, usageCaptor.getValue().inputTokens());
		assertEquals(80, usageCaptor.getValue().outputTokens());
	}

	private static ChatResponse chatResponse(String modelName, int inputTokens, int outputTokens) {
		return ChatResponse.builder()
				.aiMessage(dev.langchain4j.data.message.AiMessage.from("1|translation"))
				.modelName(modelName)
				.tokenUsage(new TokenUsage(inputTokens, outputTokens))
				.build();
	}
}

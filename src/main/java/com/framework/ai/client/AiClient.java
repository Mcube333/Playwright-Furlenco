package com.framework.ai.client;

import com.framework.ai.model.AiRequest;
import com.framework.ai.model.AiResponse;

/**
 * Provider-agnostic abstraction for AI LLM interactions.
 *
 * Specific provider implementations (e.g. Gemini, OpenAI-compatible, Ollama)
 * will be added in subsequent phases.
 */
public interface AiClient {

    /**
     * Sends an AI request to the underlying LLM provider and returns the structured response.
     *
     * @param request the provider-agnostic AI request containing prompt and configuration
     * @return the provider-agnostic AI response containing generated content and metadata
     */
    AiResponse generate(AiRequest request);

    /**
     * Returns the name of the AI provider (e.g. "gemini", "openai", "ollama").
     *
     * @return provider identifier string
     */
    String getProviderName();

    /**
     * Checks whether the client is available and properly configured (e.g. credentials and endpoints present).
     *
     * @return true if the provider client can accept requests, false otherwise
     */
    boolean isAvailable();
}

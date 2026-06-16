package com.aitestforge.service.recipe;

/**
 * AI body 생성이 재시도 후에도 실패할 때 발생하는 예외.
 */
public class AiBodyGenerationException extends RuntimeException {

    public AiBodyGenerationException(String message) {
        super(message);
    }

    public AiBodyGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

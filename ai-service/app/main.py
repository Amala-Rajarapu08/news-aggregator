import logging
import os
from threading import Lock

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field, field_validator

logger = logging.getLogger(__name__)
MODEL_NAME = os.getenv(
    "EMBEDDING_MODEL_NAME", "sentence-transformers/all-MiniLM-L6-v2"
)
EMBEDDING_DIMENSIONS = 384
_model = None
_model_lock = Lock()

app = FastAPI(
    title="PulseAI AI Service",
    description="Health checks and versioned article/query embedding inference.",
    version="0.2.0",
)


class EmbeddingRequest(BaseModel):
    texts: list[str] = Field(min_length=1, max_length=32)

    @field_validator("texts")
    @classmethod
    def validate_texts(cls, values: list[str]) -> list[str]:
        cleaned = [value.strip() for value in values]
        if any(not value or len(value) > 12_000 for value in cleaned):
            raise ValueError("Each text must contain 1 to 12000 characters.")
        return cleaned


def _encode_texts(texts: list[str]) -> list[list[float]]:
    global _model
    if _model is None:
        with _model_lock:
            if _model is None:
                from sentence_transformers import SentenceTransformer

                _model = SentenceTransformer(
                    MODEL_NAME, device=os.getenv("EMBEDDING_DEVICE", "cpu")
                )
    encoded = _model.encode(
        texts,
        batch_size=min(len(texts), 32),
        convert_to_numpy=True,
        normalize_embeddings=True,
        show_progress_bar=False,
    )
    if encoded.ndim != 2 or encoded.shape != (len(texts), EMBEDDING_DIMENSIONS):
        raise ValueError("Configured model returned an unexpected embedding shape.")
    return encoded.tolist()


@app.get("/health", tags=["health"])
def health() -> dict[str, str]:
    return {"status": "ok", "service": "pulseai-ai-service"}


@app.post("/v1/embeddings", tags=["embeddings"])
def create_embeddings(request: EmbeddingRequest) -> dict[str, object]:
    try:
        vectors = _encode_texts(request.texts)
    except Exception as exception:
        logger.warning(
            "Embedding inference unavailable exceptionType=%s",
            type(exception).__name__,
        )
        raise HTTPException(
            status_code=503, detail="The configured embedding model is unavailable."
        ) from exception
    return {
        "model": MODEL_NAME,
        "dimensions": EMBEDDING_DIMENSIONS,
        "embeddings": vectors,
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        app,
        host=os.getenv("AI_SERVICE_HOST", "127.0.0.1"),
        port=int(os.getenv("AI_SERVICE_PORT", "8000")),
        ws="none",
    )

import math

from fastapi.testclient import TestClient

from app import main


def test_embedding_endpoint_returns_model_and_normalized_dimension(monkeypatch) -> None:
    monkeypatch.setattr(
        main,
        "_encode_texts",
        lambda texts: [
            [1 / math.sqrt(main.EMBEDDING_DIMENSIONS)] * main.EMBEDDING_DIMENSIONS
            for _ in texts
        ],
    )
    client = TestClient(main.app)

    response = client.post("/v1/embeddings", json={"texts": ["solar research", "space policy"]})

    assert response.status_code == 200
    payload = response.json()
    assert payload["model"] == main.MODEL_NAME
    assert payload["dimensions"] == main.EMBEDDING_DIMENSIONS
    assert len(payload["embeddings"]) == 2
    assert all(len(vector) == main.EMBEDDING_DIMENSIONS for vector in payload["embeddings"])
    assert all(math.isclose(sum(value * value for value in vector), 1, abs_tol=1e-5)
               for vector in payload["embeddings"])


def test_embedding_endpoint_rejects_empty_or_oversized_text() -> None:
    client = TestClient(main.app)

    assert client.post("/v1/embeddings", json={"texts": ["  "]}).status_code == 422
    assert client.post("/v1/embeddings", json={"texts": ["x" * 12_001]}).status_code == 422

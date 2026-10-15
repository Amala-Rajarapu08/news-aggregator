# PulseAI AI service

FastAPI provides health checks and versioned article/query embeddings for
semantic search. Embeddings use the same configured Sentence Transformers
model (`sentence-transformers/all-MiniLM-L6-v2` by default), CPU inference,
and normalized 384-dimensional vectors.

## Local setup

Use Python 3.10 or later:

```powershell
Set-Location ai-service
if (-not (Test-Path -LiteralPath .venv)) { py -3.10 -m venv .venv }
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements-dev.txt
$env:HF_HOME = Join-Path (Get-Location) '.runtime\huggingface'
uvicorn app.main:app --host 127.0.0.1 --port 8000 --ws none
```

The service listens on `http://127.0.0.1:8000` by default. Verify it with
`Invoke-RestMethod http://127.0.0.1:8000/health`. Run tests with `pytest`.

The first embedding request downloads the model unless it is already present
in the Hugging Face cache. `EMBEDDING_MODEL_NAME` must match the backend's
`AI_EMBEDDING_MODEL`; `EMBEDDING_DEVICE` defaults to `cpu`. Requests accept 1
to 32 texts, each 1 to 12,000 characters. `AI_SERVICE_HOST` and
`AI_SERVICE_PORT` configure direct execution. Keep the inference endpoint on a
trusted local network and do not put credentials in this service's source.

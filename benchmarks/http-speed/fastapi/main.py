"""Minimal pure-HTTP FastAPI bench app — no auth, sessions, or ORM."""

from fastapi import FastAPI
from fastapi.responses import PlainTextResponse

app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)


@app.get("/ping", response_class=PlainTextResponse)
def ping() -> str:
    return "pong"


@app.get("/json")
def json_payload() -> dict:
    return {"message": "hello", "ok": True, "n": 42}

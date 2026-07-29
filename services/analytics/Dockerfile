FROM python:3.13-slim

WORKDIR /app

RUN groupadd -r analytics && useradd -r -g analytics analytics

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app app
COPY pytest.ini .

RUN chown -R analytics:analytics /app
USER analytics

EXPOSE 8001

# Shell form so PORT, which PaaS platforms assign at runtime, is expanded.
CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8001}"]

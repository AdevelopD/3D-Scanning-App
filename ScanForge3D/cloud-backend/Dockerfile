FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

# System packages + COLMAP (CPU-only, no CUDA needed)
RUN apt-get update && apt-get install -y \
    build-essential \
    cmake \
    git \
    colmap \
    python3 \
    python3-pip \
    libgl1 \
    libglib2.0-0 \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
RUN pip3 install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8000

CMD ["python3", "-m", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]

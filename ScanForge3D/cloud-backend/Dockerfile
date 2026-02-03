FROM condaforge/miniforge3:latest

RUN apt-get update && apt-get install -y \
    build-essential \
    cmake \
    git \
    libgl1-mesa-glx \
    && rm -rf /var/lib/apt/lists/*

RUN conda install -c conda-forge pythonocc-core=7.7.2 -y

WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt

COPY . .

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]

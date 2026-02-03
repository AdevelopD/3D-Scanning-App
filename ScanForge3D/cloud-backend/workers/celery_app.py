"""Celery application setup for async task processing."""

from celery import Celery

celery_app = Celery(
    "scanforge",
    broker="redis://redis:6379/0",
    backend="redis://redis:6379/1",
)

celery_app.conf.update(
    task_serializer="json",
    accept_content=["json"],
    result_serializer="json",
    timezone="UTC",
    enable_utc=True,
    task_track_started=True,
    task_time_limit=600,  # 10 min hard limit
    task_soft_time_limit=540,  # 9 min soft limit
    worker_max_tasks_per_child=10,  # Restart worker after 10 tasks (memory cleanup)
)

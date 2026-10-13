-- 保留现有向量与原文；旧向量来源未知，重建后才能进入当前模型检索。
ALTER TABLE knowledge_chunks ADD COLUMN IF NOT EXISTS embedding_identity TEXT;
ALTER TABLE knowledge_chunks ADD COLUMN IF NOT EXISTS embedding_local vector(1024);
CREATE INDEX IF NOT EXISTS knowledge_chunks_embedding_identity_idx ON knowledge_chunks (embedding_identity);
CREATE INDEX IF NOT EXISTS knowledge_chunks_embedding_local_idx
    ON knowledge_chunks USING hnsw (embedding_local vector_cosine_ops);

CREATE TABLE stored_files (
    id UUID NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_key UUID NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_stored_files PRIMARY KEY (id),
    CONSTRAINT uk_stored_files_storage_key UNIQUE (storage_key)
);

CREATE INDEX idx_stored_files_uploaded_at ON stored_files (uploaded_at DESC, id DESC);

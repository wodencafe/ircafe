-- Keep the large append-only chat log on disk and bounded by HSQLDB's row cache.

SET TABLE chat_log TYPE CACHED;

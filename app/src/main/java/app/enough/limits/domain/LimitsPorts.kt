package app.enough.limits.domain

interface ConfigPort {
    suspend fun get(): Config
    suspend fun update(f: suspend (Config) -> Config): Config
}

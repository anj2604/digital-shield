from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env")

    database_url: str = "postgresql+psycopg2://shield:shield_dev@localhost:5433/digital_shield"  # legacy prototype
    mongo_uri: str = "mongodb://localhost:27017"
    mongo_db: str = "digital_shield"
    neo4j_uri: str = "bolt://localhost:7687"
    neo4j_user: str = "neo4j"
    neo4j_password: str = "shield_dev"
    secret_key: str = "dev-only-change-me-digital-shield-2026-secret-key"   # set a real one in .env
    token_hours: int = 12
    invite_hours: int = 72

    # Email: leave smtp_host empty for dev mode (emails are printed and kept in the outbox)
    smtp_host: str = ""
    smtp_port: int = 587
    smtp_user: str = ""
    smtp_password: str = ""
    mail_from: str = ""
    vt_api_key: str = ""   # optional VirusTotal fingerprint lookups

settings = Settings()

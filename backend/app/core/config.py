from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    database_url: str = "postgresql://shield:shield_dev@localhost:5432/digital_shield"
    neo4j_uri: str = "bolt://localhost:7687"
    neo4j_user: str = "neo4j"
    neo4j_password: str = "shield_dev"
    secret_key: str = "change-me"

    class Config:
        env_file = ".env"

settings = Settings()

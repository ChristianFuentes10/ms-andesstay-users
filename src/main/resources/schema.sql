-- schema.sql - Creacion idempotente de las tablas de usuarios para PostgreSQL / RDS

CREATE TABLE IF NOT EXISTS users (
    id          UUID            PRIMARY KEY,
    cognito_sub VARCHAR(100)    UNIQUE,
    email       VARCHAR(254)    NOT NULL,
    nombre      VARCHAR(100)    NOT NULL,
    apellido    VARCHAR(100),
    telefono    VARCHAR(30),
    estado      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVO',
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_estado CHECK (estado IN ('ACTIVO', 'INACTIVO'))
);

-- Unicidad de email sin distinguir mayusculas/minusculas
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_lower ON users (LOWER(email));
CREATE INDEX IF NOT EXISTS ix_users_estado ON users (estado);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT ck_user_roles_role CHECK (role IN ('HUESPED', 'RECEPCIONISTA', 'ADMIN', 'AUDITOR'))
);

CREATE INDEX IF NOT EXISTS ix_user_roles_role ON user_roles (role);

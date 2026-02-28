CREATE TABLE IF NOT EXISTS cashbox_mode (
    code VARCHAR(32) PRIMARY KEY,
    name_ru TEXT NOT NULL,
    name_en TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS cashbox_state (
    code VARCHAR(32) PRIMARY KEY,
    name_ru TEXT NOT NULL,
    name_en TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS ofd_provider (
    code VARCHAR(64) PRIMARY KEY,
    name_ru TEXT NOT NULL,
    name_en TEXT NOT NULL
);

INSERT INTO cashbox_mode (code, name_ru, name_en) VALUES
    ('REGISTRATION', 'Регистрация', 'Registration'),
    ('PROGRAMMING', 'Программирование', 'Programming')
ON CONFLICT (code) DO NOTHING;

INSERT INTO cashbox_state (code, name_ru, name_en) VALUES
    ('IDLE', 'Ожидание', 'Idle'),
    ('ACTIVE', 'Активна', 'Active'),
    ('BLOCKED', 'Заблокирована', 'Blocked')
ON CONFLICT (code) DO NOTHING;

INSERT INTO ofd_provider (code, name_ru, name_en) VALUES
    ('KAZAKHTELECOM', 'Казахтелеком', 'Kazakhtelecom')
ON CONFLICT (code) DO NOTHING;

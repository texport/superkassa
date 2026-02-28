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

INSERT IGNORE INTO cashbox_mode (code, name_ru, name_en) VALUES
    ('REGISTRATION', 'Регистрация', 'Registration'),
    ('PROGRAMMING', 'Программирование', 'Programming');

INSERT IGNORE INTO cashbox_state (code, name_ru, name_en) VALUES
    ('IDLE', 'Ожидание', 'Idle'),
    ('ACTIVE', 'Активна', 'Active'),
    ('BLOCKED', 'Заблокирована', 'Blocked');

INSERT IGNORE INTO ofd_provider (code, name_ru, name_en) VALUES
    ('KAZAKHTELECOM', 'Казахтелеком', 'Kazakhtelecom');

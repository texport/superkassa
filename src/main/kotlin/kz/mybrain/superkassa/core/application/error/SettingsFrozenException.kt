package kz.mybrain.superkassa.core.application.error

class SettingsFrozenException(message: String) : ServiceException("SETTINGS_FROZEN", 403, message)

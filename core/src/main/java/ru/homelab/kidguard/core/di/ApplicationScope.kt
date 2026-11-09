package ru.homelab.kidguard.core.di

import javax.inject.Qualifier

/**
 * Scope, живущий весь процесс приложения.
 *
 * Нужен тем, кто держит общий ресурс на всё время жизни процесса (например, единая временная
 * шкала): его scope должен пережить отдельные экраны и сервисы, но умереть вместе с процессом.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

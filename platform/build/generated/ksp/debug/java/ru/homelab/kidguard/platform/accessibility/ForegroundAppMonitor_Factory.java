package ru.homelab.kidguard.platform.accessibility;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class ForegroundAppMonitor_Factory implements Factory<ForegroundAppMonitor> {
  @Override
  public ForegroundAppMonitor get() {
    return newInstance();
  }

  public static ForegroundAppMonitor_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ForegroundAppMonitor newInstance() {
    return new ForegroundAppMonitor();
  }

  private static final class InstanceHolder {
    static final ForegroundAppMonitor_Factory INSTANCE = new ForegroundAppMonitor_Factory();
  }
}

package ru.homelab.kidguard.platform.accessibility;

import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

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
public final class KidGuardAccessibilityService_MembersInjector implements MembersInjector<KidGuardAccessibilityService> {
  private final Provider<ForegroundAppMonitor> foregroundAppMonitorProvider;

  private KidGuardAccessibilityService_MembersInjector(
      Provider<ForegroundAppMonitor> foregroundAppMonitorProvider) {
    this.foregroundAppMonitorProvider = foregroundAppMonitorProvider;
  }

  @Override
  public void injectMembers(KidGuardAccessibilityService instance) {
    injectForegroundAppMonitor(instance, foregroundAppMonitorProvider.get());
  }

  public static MembersInjector<KidGuardAccessibilityService> create(
      Provider<ForegroundAppMonitor> foregroundAppMonitorProvider) {
    return new KidGuardAccessibilityService_MembersInjector(foregroundAppMonitorProvider);
  }

  @InjectedFieldSignature("ru.homelab.kidguard.platform.accessibility.KidGuardAccessibilityService.foregroundAppMonitor")
  public static void injectForegroundAppMonitor(KidGuardAccessibilityService instance,
      ForegroundAppMonitor foregroundAppMonitor) {
    instance.foregroundAppMonitor = foregroundAppMonitor;
  }
}

package ru.homelab.kidguard.platform.permissions;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class PermissionsManager_Factory implements Factory<PermissionsManager> {
  private final Provider<Context> contextProvider;

  private PermissionsManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public PermissionsManager get() {
    return newInstance(contextProvider.get());
  }

  public static PermissionsManager_Factory create(Provider<Context> contextProvider) {
    return new PermissionsManager_Factory(contextProvider);
  }

  public static PermissionsManager newInstance(Context context) {
    return new PermissionsManager(context);
  }
}

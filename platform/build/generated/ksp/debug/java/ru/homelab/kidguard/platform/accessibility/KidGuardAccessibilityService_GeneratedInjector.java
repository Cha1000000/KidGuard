package ru.homelab.kidguard.platform.accessibility;

import dagger.hilt.InstallIn;
import dagger.hilt.android.components.ServiceComponent;
import dagger.hilt.codegen.OriginatingElement;
import dagger.hilt.internal.GeneratedEntryPoint;
import javax.annotation.processing.Generated;

@OriginatingElement(
    topLevelClass = KidGuardAccessibilityService.class
)
@GeneratedEntryPoint
@InstallIn(ServiceComponent.class)
@Generated("dagger.hilt.android.processor.internal.androidentrypoint.InjectorEntryPointGenerator")
public interface KidGuardAccessibilityService_GeneratedInjector {
  void injectKidGuardAccessibilityService(
      KidGuardAccessibilityService kidGuardAccessibilityService);
}

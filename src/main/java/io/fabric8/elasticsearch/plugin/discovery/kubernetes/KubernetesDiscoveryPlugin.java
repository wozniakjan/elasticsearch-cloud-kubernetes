/**
 * Copyright (C) 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.elasticsearch.plugin.discovery.kubernetes;

import io.fabric8.elasticsearch.cloud.kubernetes.KubernetesAPIService;
import io.fabric8.elasticsearch.cloud.kubernetes.KubernetesModule;
import io.fabric8.elasticsearch.discovery.kubernetes.KubernetesDiscovery;
import io.fabric8.elasticsearch.discovery.kubernetes.KubernetesUnicastHostsProvider;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.discovery.DiscoveryModule;
import org.elasticsearch.plugins.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class KubernetesDiscoveryPlugin extends Plugin {

  protected final ESLogger logger = Loggers.getLogger(KubernetesDiscoveryPlugin.class);
  private final Settings settings;

  public KubernetesDiscoveryPlugin(Settings settings) {
    this.settings = settings;
  }

  /**
   * Check if discovery is meant to start
   *
   * @return true if we can start kubernetes discovery features
   */
  public static boolean isDiscoveryAlive(Settings settings, ESLogger logger) {
    // User set discovery.type: kubernetes
    if (!KubernetesDiscovery.KUBERNETES.equalsIgnoreCase(settings.get("discovery.type"))) {
      logger.debug("discovery.type not set to {}", KubernetesDiscovery.KUBERNETES);
      return false;
    }

    if ( !hasNamespace(settings, logger) || !(hasServiceName(settings, logger) ^ hasPodLabel(settings, logger)) ) {
      logger.debug("one or more Kubernetes discovery settings are missing. " +
          "Check elasticsearch.yml file. Should have [{}] and only one of [{}] or [{} : {}].",
        KubernetesAPIService.Fields.NAMESPACE,
        KubernetesAPIService.Fields.SERVICE_NAME,
        KubernetesAPIService.Fields.POD_LABEL,
        KubernetesAPIService.Fields.POD_PORT);
      return false;
    }

    logger.trace("all required properties for Kubernetes discovery are set!");

    return true;
  }

  private static boolean hasNamespace(Settings settings, ESLogger logger) {
    return checkProperty(KubernetesAPIService.Fields.NAMESPACE, settings.get(KubernetesAPIService.Fields.NAMESPACE), logger);
  }
 
  private static boolean hasServiceName(Settings settings, ESLogger logger) {
    return checkProperty(KubernetesAPIService.Fields.SERVICE_NAME, settings.get(KubernetesAPIService.Fields.SERVICE_NAME), logger);
  }
 
  private static boolean hasPodLabel(Settings settings, ESLogger logger) {
    return checkProperty(KubernetesAPIService.Fields.POD_LABEL, settings.get(KubernetesAPIService.Fields.POD_LABEL), logger) &&
           checkProperty(KubernetesAPIService.Fields.POD_PORT, settings.get(KubernetesAPIService.Fields.POD_PORT), logger);
  }
  
  private static boolean checkProperty(String name, String value, ESLogger logger) {
    if (!Strings.hasText(value)) {
      logger.warn("{} is not set.", name);
      return false;
    }
    return true;
  }

  @Override
  public String name() {
    return "cloud-kubernetes";
  }

  @Override
  public String description() {
    return "Cloud Kubernetes Discovery Plugin";
  }

  @Override
  public Collection<Module> nodeModules() {
    List<Module> modules = new ArrayList<>();
    if (isDiscoveryAlive(settings, logger)) {
      modules.add(new KubernetesModule());
    }
    return modules;
  }

  @Override
  public Collection<Class<? extends LifecycleComponent>> nodeServices() {
    Collection<Class<? extends LifecycleComponent>> services = new ArrayList<>();
    if (isDiscoveryAlive(settings, logger)) {
      services.add(KubernetesModule.getComputeServiceImpl());
    }
    return services;
  }

  public void onModule(DiscoveryModule discoveryModule) {
    if (isDiscoveryAlive(settings, logger)) {
      discoveryModule.addDiscoveryType("kubernetes", KubernetesDiscovery.class);
      discoveryModule.addUnicastHostProvider(KubernetesUnicastHostsProvider.class);
    }
  }
}

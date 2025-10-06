package com.nucleonforge.axile.master.service.discovery;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;

import io.fabric8.kubernetes.api.model.Pod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesServiceInstance;
import org.springframework.cloud.kubernetes.fabric8.discovery.KubernetesDiscoveryClient;

import com.nucleonforge.axile.common.api.registration.ServiceMetadata;
import com.nucleonforge.axile.common.domain.Instance;
import com.nucleonforge.axile.common.domain.InstanceId;
import com.nucleonforge.axile.master.service.transport.ManagedServiceMetadataEndpointProber;

/**
 * Kubernetes implementation of {@link InstancesDiscoverer}.
 * <p>This service discovers running instances of services registered
 * in a Kubernetes cluster.</p>
 *
 * @author Mikhail Polivakha
 */
public class KubernetesInstanceDiscoverer extends AbstractInstancesDiscoverer {

    private static final Logger log = LoggerFactory.getLogger(KubernetesInstanceDiscoverer.class);
    private final KubernetesDiscoveryClient kubernetesDiscoveryClient;

    public KubernetesInstanceDiscoverer(
            KubernetesDiscoveryClient discoveryClient,
            ManagedServiceMetadataEndpointProber managedServiceMetadataEndpointProber) {

        super(log, discoveryClient, managedServiceMetadataEndpointProber);
        this.kubernetesDiscoveryClient = discoveryClient;
    }

    @Override
    @SuppressWarnings("NullAway")
    protected Instance toDomainInstance(InstanceIntermediateProfile profile) throws IllegalArgumentException {
        ServiceInstance serviceInstance = profile.serviceInstance();

        if (serviceInstance instanceof KubernetesServiceInstance k8sInstance) {

            if (k8sInstance.getMetadata() == null) {
                throw new IllegalArgumentException(
                        "Unable to register K8S pod '%s' as a managed instance - no metadata present on the pod"
                                .formatted(serviceInstance.getInstanceId()));
            }

            PodMetaData podMetaData = getPodMetaData(k8sInstance);

            return new Instance(
                    InstanceId.of(k8sInstance.getInstanceId()),
                    podMetaData.name(),
                    profile.metadata().serviceVersion(),
                    profile.metadata().javaVersion(),
                    profile.metadata().springBootVersion(),
                    profile.metadata().commitShortSha(),
                    podMetaData.creationTimestamp(),
                    mapStatus(profile),
                    serviceInstance.getUri().toString() + "/actuator");
        } else {
            throw new IllegalArgumentException(buildErrorMessage(serviceInstance));
        }
    }

    // TODO:
    //  That is wrong. We ideally need to create our own DiscoveryClient, because Spring Cloud's
    //  one is a complete piece of shit that is not extensible and does not provide this metadata
    @SuppressWarnings("NullAway")
    private PodMetaData getPodMetaData(KubernetesServiceInstance k8sInstance) {
        try {
            return tryGetPodMetadata(k8sInstance);
        } catch (Exception e) {
            log.warn("Unable to get additional metadata for pod: {}", k8sInstance, e);
            return new PodMetaData("", null);
        }
    }

    private PodMetaData tryGetPodMetadata(KubernetesServiceInstance k8sInstance) {
        List<Pod> pods = kubernetesDiscoveryClient
                .getClient()
                .pods()
                .inNamespace(k8sInstance.getNamespace())
                .list()
                .getItems();

        Pod k8sPod = pods.stream()
                .filter(pod -> pod.getMetadata().getUid().equalsIgnoreCase(k8sInstance.getInstanceId()))
                .findFirst()
                .orElseThrow();

        return new PodMetaData(
                k8sPod.getMetadata().getName(),
                parsePodCreationTimestamp(k8sPod.getMetadata().getCreationTimestamp()));
    }

    private static Instance.InstanceStatus mapStatus(InstanceIntermediateProfile profile) {
        ServiceMetadata.HealthStatus healthStatus = profile.metadata().healthStatus();

        if (healthStatus == null) {
            return Instance.InstanceStatus.UNKNOWN;
        }

        return switch (healthStatus) {
            case UP -> Instance.InstanceStatus.UP;
            case DOWN -> Instance.InstanceStatus.DOWN;
            case UNKNOWN -> Instance.InstanceStatus.UNKNOWN;
        };
    }

    record PodMetaData(String name, Instant creationTimestamp) {}

    @SuppressWarnings("NullAway")
    private Instant parsePodCreationTimestamp(String creationTimestamp) {
        try {
            TemporalAccessor temporal =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.Z").parse(creationTimestamp);
            return Instant.from(temporal);
        } catch (DateTimeException e) {
            log.warn(
                    """
                Unable to parse the deployment timestamp {} of the pod : {}.
                That will affect the corresponding service on the wallboard UI
                """,
                    e);
            return null;
        }
    }

    private static String buildErrorMessage(ServiceInstance serviceInstance) {
        return "Unable to register K8S pod '%s' as a managed instance - expected %s to be an instance of %s, but actually is %s"
                .formatted(
                        serviceInstance.getInstanceId(),
                        ServiceInstance.class.getSimpleName(),
                        KubernetesServiceInstance.class.getName(),
                        serviceInstance.getClass().getName());
    }
}

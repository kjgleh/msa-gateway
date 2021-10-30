package me.kjgleh.msa.gateway.configs

import io.swagger.v3.core.filter.SpecFilter
import org.springdoc.core.SwaggerUiConfigParameters
import org.springframework.beans.factory.InitializingBean
import org.springframework.cloud.gateway.route.RouteDefinitionLocator
import org.springframework.cloud.gateway.support.NotFoundException
import org.springframework.context.annotation.Configuration
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

@Configuration
@RestController
class SwaggerConfig(
    private val routeDefinitionLocator: RouteDefinitionLocator,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    private val swaggerUiConfigParameters: SwaggerUiConfigParameters,
) : SpecFilter(), InitializingBean {

    companion object {
        private const val APIDOC = "/v3/api-docs"
    }

    private val restTemplate = RestTemplate()

    private val services = mutableMapOf<String, Pair<String, String>>()

    /**
     * 1. collect services in gateway routing configuration
     * 1. add service to swagger ui group
     * 1. hold route path and service url on [services]
     */
    override fun afterPropertiesSet() {
        val routeDefinitions =
            routeDefinitionLocator.routeDefinitions.collectList().block()!!
        routeDefinitions.filter {   // filter only has "-service" suffix
            it.id.endsWith("-service")
        }.forEach {
            // it.id => service name
            // it.uri => service url
            val name = it.id.replace("-service", "")
            swaggerUiConfigParameters.addGroup(name)
            val gatewayMappingPath =
                it.predicates.first().args.values.first().replace("/**", "")
            services[name] = Pair(gatewayMappingPath, "${it.uri}$APIDOC")
        }
        swaggerUiConfigParameters.addUrl(APIDOC)
    }

    /**
     * serve other service's api-docs
     */
    @GetMapping("$APIDOC/{name}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun apiDocs(
        @PathVariable name: String,
        request: ServerHttpRequest,
    ): Map<String, Any?> {
        val (gatewayMappingPath, serviceUrl) = services[name]
            ?: throw NotFoundException("$name is not configured")
        // get service's api-doc json as HashMap
        val apiDoc = restTemplate.exchange(
            RequestEntity.get(URI(serviceUrl))
                .accept(MediaType.APPLICATION_JSON).build(),
            object : ParameterizedTypeReference<HashMap<String, Any?>>() {}
        ).body ?: throw NotFoundException("Get $serviceUrl fail")
        // change server url to gatewayMappedServiceUrl
        apiDoc["servers"] = listOf(
            mapOf(
                "url" to UriComponentsBuilder.fromHttpRequest(request)
                    .replacePath(gatewayMappingPath).toUriString(),
            )
        )
        return apiDoc
    }
}
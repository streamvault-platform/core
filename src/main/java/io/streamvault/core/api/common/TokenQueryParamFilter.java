package io.streamvault.core.api.common;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/**
 * Runs before the MP-JWT mechanism. Promotes ?token=<jwt> to
 * Authorization: Bearer <jwt> so HTMLAudio on web can authenticate
 * without custom request headers. Returns null to let JWT handle auth.
 */
@ApplicationScoped
@Priority(2001)
public class TokenQueryParamFilter implements HttpAuthenticationMechanism {

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        String token = context.request().getParam("token");
        if (token != null && context.request().getHeader("Authorization") == null) {
            context.request().headers().set("Authorization", "Bearer " + token);
        }
        return Uni.createFrom().nullItem();
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().nullItem();
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of();
    }
}

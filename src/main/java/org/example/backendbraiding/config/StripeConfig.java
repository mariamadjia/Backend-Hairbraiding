package org.example.backendbraiding.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {
    
    @Value("${stripe.api.key}")
    private String stripeSecretKey;

    @Value("${stripe.webhook.secret}")
    private String stripeWebhookSecret;
    
    @PostConstruct
    public void initialize() {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()
                || !(stripeSecretKey.startsWith("sk_test_") || stripeSecretKey.startsWith("sk_live_"))
                || stripeSecretKey.contains("your_")) {
            throw new IllegalStateException("STRIPE_SECRET_KEY must be configured with a valid Stripe secret key");
        }
        if (stripeWebhookSecret == null || stripeWebhookSecret.isBlank()
                || !stripeWebhookSecret.startsWith("whsec_") || stripeWebhookSecret.contains("your_")) {
            throw new IllegalStateException("STRIPE_WEBHOOK_SECRET must be configured with a valid webhook signing secret");
        }
        Stripe.apiKey = stripeSecretKey;
    }
}
